#!/usr/bin/env node
/**
 * Reference implementation of the trusted person's "separate device" from
 * need.md section 7. ReelLock never holds this private key - it only ever
 * sees the public key (once, during setup) and signatures (one per recovery).
 *
 * Uses only Node's built-in `crypto` module - no npm install needed.
 *
 * Usage:
 *   # Run once, on the TRUSTED PERSON's own device. Keep private_key.pem
 *   # secret; paste the printed public key into ReelLock's setup wizard.
 *   node trusted_signer.js generate-key --out private_key.pem
 *
 *   # Run once per recovery request, after reviewing the challenge ReelLock
 *   # showed the device owner. Paste the printed signature back into
 *   # ReelLock's recovery screen.
 *   node trusted_signer.js sign --key private_key.pem --challenge "<paste challenge here>"
 *
 * The challenge is the exact UTF-8 string:
 *   nonce|deviceId|commitmentVersion|requestedAction|expiresAtEpochMillis
 * (see android/app/src/main/kotlin/com/reellock/reellock/recovery/RecoveryCrypto.kt
 * canonicalChallenge - this script does not reconstruct it, it signs
 * whatever string ReelLock displayed, verbatim).
 */

const crypto = require('crypto');
const fs = require('fs');

function parseArgs(argv) {
  const args = {};
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i].replace(/^--/, '');
    args[key] = argv[i + 1];
  }
  return args;
}

function generateKey(args) {
  const outPath = args.out || 'private_key.pem';

  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', {
    namedCurve: 'P-256', // secp256r1, matches RecoveryCrypto.kt's KeyFactory.getInstance("EC") + "SHA256withECDSA"
  });

  const privatePem = privateKey.export({ type: 'pkcs8', format: 'pem' });
  fs.writeFileSync(outPath, privatePem);

  // SubjectPublicKeyInfo DER, base64 - exactly what RecoveryCrypto.parsePublicKeyBase64
  // feeds into KeyFactory("EC").generatePublic(X509EncodedKeySpec(...)).
  const publicDer = publicKey.export({ type: 'spki', format: 'der' });
  const publicB64 = publicDer.toString('base64');

  console.log(`Private key written to ${outPath} - keep this secret, never send it anywhere.`);
  console.log();
  console.log("Public key (paste this into ReelLock's setup wizard, 'Trusted person' field):");
  console.log(publicB64);
}

function sign(args) {
  if (!args.key || !args.challenge) {
    console.error('error: --key and --challenge are required');
    process.exit(1);
  }

  const privatePem = fs.readFileSync(args.key, 'utf8');
  const privateKey = crypto.createPrivateKey(privatePem);

  // Matches Java's Signature.getInstance("SHA256withECDSA"): SHA-256 digest,
  // DER-encoded (r,s) signature.
  const signature = crypto.sign('sha256', Buffer.from(args.challenge, 'utf8'), {
    key: privateKey,
    dsaEncoding: 'der',
  });

  console.log("Signature (paste this into ReelLock's recovery screen, step 2):");
  console.log(signature.toString('base64'));
}

function main() {
  const [command, ...rest] = process.argv.slice(2);
  const args = parseArgs(rest);

  switch (command) {
    case 'generate-key':
      generateKey(args);
      break;
    case 'sign':
      sign(args);
      break;
    default:
      console.error('Usage: node trusted_signer.js <generate-key|sign> [--out path] [--key path] [--challenge string]');
      process.exit(1);
  }
}

main();
