#!/usr/bin/env python3
"""
Reference implementation of the trusted person's "separate device" from
need.md section 7. ReelLock never holds this private key - it only ever
sees the public key (once, during setup) and signatures (one per recovery).

Requires: pip install cryptography

Usage:
    # Run once, on the TRUSTED PERSON's own device. Keep private_key.pem
    # secret; paste the printed public key into ReelLock's setup wizard.
    python trusted_signer.py generate-key --out private_key.pem

    # Run once per recovery request, after reviewing the challenge ReelLock
    # showed the device owner. Paste the printed signature back into
    # ReelLock's recovery screen.
    python trusted_signer.py sign --key private_key.pem --challenge "<paste challenge here>"

The challenge is the exact UTF-8 string:
    nonce|deviceId|commitmentVersion|requestedAction|expiresAtEpochMillis
(see android/app/src/main/kotlin/com/reellock/reellock/recovery/RecoveryCrypto.kt
canonicalChallenge - this script does not reconstruct it, it signs
whatever string ReelLock displayed, verbatim).
"""

import argparse
import base64
import sys

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


def generate_key(args: argparse.Namespace) -> None:
    private_key = ec.generate_private_key(ec.SECP256R1())

    pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    )
    with open(args.out, "wb") as f:
        f.write(pem)

    public_der = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    public_b64 = base64.b64encode(public_der).decode("ascii")

    print(f"Private key written to {args.out} - keep this secret, never send it anywhere.")
    print()
    print("Public key (paste this into ReelLock's setup wizard, 'Trusted person' field):")
    print(public_b64)


def sign(args: argparse.Namespace) -> None:
    with open(args.key, "rb") as f:
        private_key = serialization.load_pem_private_key(f.read(), password=None)

    if not isinstance(private_key, ec.EllipticCurvePrivateKey):
        print("error: key file is not an EC private key", file=sys.stderr)
        sys.exit(1)

    challenge_bytes = args.challenge.encode("utf-8")
    signature = private_key.sign(challenge_bytes, ec.ECDSA(hashes.SHA256()))
    signature_b64 = base64.b64encode(signature).decode("ascii")

    print("Signature (paste this into ReelLock's recovery screen, step 2):")
    print(signature_b64)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    subparsers = parser.add_subparsers(dest="command", required=True)

    generate_parser = subparsers.add_parser("generate-key", help="Create a new EC P-256 keypair.")
    generate_parser.add_argument("--out", default="private_key.pem", help="Where to write the private key (PEM).")
    generate_parser.set_defaults(func=generate_key)

    sign_parser = subparsers.add_parser("sign", help="Sign a recovery challenge string.")
    sign_parser.add_argument("--key", required=True, help="Path to the private key PEM file.")
    sign_parser.add_argument("--challenge", required=True, help="The exact challenge string shown by ReelLock.")
    sign_parser.set_defaults(func=sign)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
