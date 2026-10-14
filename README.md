# ReelLock

Implementation of `need.md` - Flutter UI, native Kotlin enforcement core, local-first, no backend.

## Layout

```
app/                           Flutter project (UI) + Android host module
  lib/                         Dart UI: setup wizard, dashboard, recovery, security log
  android/app/src/main/kotlin/com/reellock/reellock/
    config/                    CommitmentConfig + ConfigValidator (R1)
    commitment/                CommitmentEngine - the single lock-state gate (R3)
    usage/                     UsageAccountingEngine + RollingWindowCalculator (R2)
    classifier/                ScreenClassifier + Instagram UI signatures (section 5)
    accessibility/              InstagramAccessibilityService (the detector)
    enforcement/                EnforcementManager + BlockOverlayManager
    recovery/                   RecoveryManager + ECDSA challenge/response (section 7)
    deviceadmin/                DevicePolicyController (Managed mode, section 8)
    monitoring/                 PermissionMonitor, BootReceiver, WorkManager workers (R6/R9)
    time/                       TrustedTimeManager - clock-tamper resistance heuristic (R7)
    persistence/                Room database (schema per need.md section 6)
    bridge/, MainActivity.kt    Flutter <-> Kotlin MethodChannel/EventChannel
  android/app/src/test/kotlin/  JVM unit tests (27 tests, see "What's tested" below)
tools/trusted_signer.js        Reference offline signer for the trusted person's device (Node built-ins only)
tools/trusted_signer.py        Same thing, Python version (needs `pip install cryptography`)
```

## Building and running

```
cd app
flutter pub get
flutter build apk --debug      # or: flutter run -d <device-id>
```

Verified in this environment: `flutter analyze` (clean), `flutter test` (2/2 passing),
`./gradlew :app:assembleDebug` (builds), `./gradlew :app:testDebugUnitTest` (28/28 passing,
including a Node-generated key+signature verified against the Kotlin side -
see `NodeInteropTest.kt`). **Not verified here** (no physical device/emulator was
available in this session): the accessibility service against a real, current Instagram
build; boot-receiver behavior across an actual reboot; device-owner provisioning. See
"Known limitations and what still needs device testing" below.

### One Gradle version note

`android/settings.gradle.kts` pins Kotlin to 2.2.20, one notch below the Flutter
template's current default (2.4.0). Kotlin 2.4.0's bytecode metadata is newer than
what `kapt` (used for Room's annotation processing) can read yet - building with the
template default fails with `Provided Metadata instance has version 2.4.0, while
maximum supported version is 2.3.0`. 2.2.20 is the lowest version that also satisfies
this Flutter tool's own minimum-Kotlin-version check. If you later migrate Room to KSP
and this app to AGP's built-in-Kotlin mode, that pin can likely be removed.

## First-run setup on a device

1. Install the app, open it, go through the setup wizard.
2. A trusted person's EC (P-256) public key is **required** before activation. On
   their own device (needs only Node, already installed almost everywhere - no
   `npm install`): `node tools/trusted_signer.js generate-key`, then paste the
   printed public key into the wizard. (Python alternative: `pip install
   cryptography && python tools/trusted_signer.py generate-key`.)
3. Activate. The app will prompt you to enable the accessibility service
   (Settings > Accessibility > ReelLock) - this is what actually detects Reels.
4. Standard mode (default) works from here. Managed mode additionally requires
   device-owner provisioning, which is **out-of-band** (see below) - the app itself
   never silently escalates its own privileges.

### MIUI / Xiaomi / Redmi devices: two extra steps

MIUI kills an app's entire process - including the bound accessibility service
living in it - when its task is swiped from Recents, *in addition to* standard
Android's own process-death behavior. A foreground service with a persistent
notification (`ReelLockForegroundService`) covers the standard Android case, but
MIUI's own kill switch needs manually clearing:

1. Settings > Apps > Manage apps > ReelLock > **Autostart** - turn **on**.
2. Settings > Battery > App battery saver > ReelLock - set to **No restrictions**.

Without these, usage tracking can silently stop the moment the app is swiped
away, no matter how much Reels gets watched afterward - this isn't a bug in the
app, it's OEM-level process management that the accessibility API itself has no
override for.

### Managed mode / device-owner provisioning (optional, section 8)

This cannot be done by the app itself and is not something to do on a daily-driver
phone without understanding the consequences (need.md is explicit about this). On a
device you are willing to provision, typically via ADB on a freshly-reset device:

```
adb shell dpm set-device-owner com.reellock.reellock/.deviceadmin.ReelLockDeviceAdminReceiver
```

If this is not run, `DevicePolicyController.isDeviceOwner()` returns false and
every managed-mode call becomes a documented no-op - the dashboard shows standard-mode
protection only, never a false "managed" claim (R9).

## Recovery flow (section 7)

1. On the device, open "Request trusted-person recovery", pick an action, submit.
2. ReelLock shows a challenge string. Send it to the trusted person out-of-band
   (text message, photo, however).
3. They run `node tools/trusted_signer.js sign --key private_key.pem --challenge
   "<paste>"` on their own device and send back the printed signature.
4. Paste the signature into step 2 of the recovery screen. ReelLock verifies it
   against the public key pinned during setup - not a key the request itself
   supplies - before applying anything.

`EXTEND_ALLOWANCE_TODAY` is accepted as a request type and logged to the audit
trail, but **has no enforcement effect** in this build - there is no "bonus seconds"
ledger in `UsageAccountingEngine` yet. `UNLOCK_CONFIG_EDIT` and `RESET_COMMITMENT`
are fully implemented.

## What's tested

Automated, pure-JVM (no device/emulator needed), run with
`./gradlew :app:testDebugUnitTest` and `flutter test`:

- `ConfigValidatorTest` - allowance/window/commitment-day range and consistency rules (R1).
- `RollingWindowCalculatorTest` - the rolling 5-hour budget math (R2), including
  partial overlap at window boundaries, open/ongoing intervals, and exhaustion to zero.
- `ScreenClassifierTest` - the four-outcome classifier (REELS/NON_REELS/TRANSITION/
  UNKNOWN) against hand-built node trees, including the "a stray 'Reels' label inside
  a DM or feed screen must not misclassify it" case need.md calls out explicitly.
- `RecoveryCryptoTest` - ECDSA sign/verify round trip, rejects a tampered challenge,
  rejects a signature from the wrong keypair, rejects a garbage public key.
- Flutter `widget_test.dart` - the root router opens the setup wizard with no
  commitment and the dashboard with an active one, driven through a mocked
  MethodChannel (no real native code involved).

## Known limitations and what still needs device testing

These are the honest gaps, consistent with need.md's own repeated warnings that this
is not a tamper-proof boundary against a compromised device or a determined owner:

- **Instagram UI signatures will drift.** `InstagramUiSignatures.kt` lists resource-id
  substrings observed at the time of writing. Instagram changes its UI without
  notice. Nothing in this codebase can test against a real, current Instagram build
  without a device - do that before trusting this for real enforcement, and expect to
  update the signature list.
- **CommitmentEngine / UsageAccountingEngine integration is untested in this session.**
  Both depend on Room + `TrustedTimeManager` (which needs Android Keystore via
  `EncryptedSharedPreferences`), so they need Robolectric or a real device/emulator,
  neither of which was available here. The algorithms they call
  (`RollingWindowCalculator`, `ConfigValidator`) are unit-tested; the DB wiring,
  reboot reconciliation, and the Keystore-backed clock anchor are not.
- **Clock-tamper resistance is a heuristic, not a guarantee** (need.md is explicit
  about this): `TrustedTimeManager` trusts the wall clock whenever it agrees with
  elapsed time within a 2-minute tolerance, and falls back to an elapsed-time-anchored
  estimate otherwise. A sustained, gradual clock drift introduced faster than this
  check runs could still slip through; a fully offline device has no independent time
  authority to check against.
- **Standard mode cannot prevent uninstall, Safe Mode, or disabling accessibility** -
  by design (need.md explicit non-goal). `PermissionMonitor` detects and reports loss
  (via a 15-minute `WorkManager` poll - Android has no push notification for "user
  disabled this permission"), it does not prevent it.
- **trusted_signer.js is verified against the Kotlin side** (`NodeInteropTest.kt`:
  a key and signature generated by the script are checked against
  `RecoveryCrypto.verify` directly). **trusted_signer.py is not** - no Python
  interpreter was available in this session to confirm its output round-trips the
  same way. Its encoding choices mirror the JS version, so it should interoperate,
  but verify it yourself before depending on it.
- **No "remaining time" HUD while actively watching Reels** - only the block overlay
  at exhaustion is implemented; the dashboard's remaining-budget bar only updates on
  open/refresh/block events, not continuously second-by-second.
- **Play Store accessibility disclosure**: `strings.xml`'s service description is
  written to be truthful for Play's Accessibility API review, but this was never
  submitted for actual review - re-check current Play policy before any real
  distribution (need.md section 10).
- **iOS**: need.md scopes this to Android only; the Flutter project was created
  Android-only (`flutter create --platforms android`).
