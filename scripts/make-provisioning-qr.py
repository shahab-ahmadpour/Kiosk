#!/usr/bin/env python3
"""Build the Android Enterprise provisioning QR for a factory-reset tablet.

The QR contains the Wi-Fi password and the staff PIN. Keep the PNG off the showroom floor.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import subprocess
import sys
from pathlib import Path


COMPONENT = "com.showroom.kiosk/com.showroom.kiosk.AdminReceiver"


def load_props(path: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def checksum(data: bytes) -> str:
    return base64.urlsafe_b64encode(hashlib.sha256(data).digest()).decode("ascii").rstrip("=")


def cert_checksum(keystore: Path, alias: str, storepass: str) -> str:
    der = subprocess.check_output(
        [
            "keytool",
            "-exportcert",
            "-alias",
            alias,
            "-keystore",
            str(keystore),
            "-storepass",
            storepass,
        ]
    )
    return checksum(der)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description="Write a device-owner provisioning QR")
    parser.add_argument("--apk", type=Path, default=root / "app/build/outputs/apk/release/app-release.apk")
    parser.add_argument("--url", required=True, help="HTTPS url of that exact APK file")
    parser.add_argument("--pin", required=True, help="6 to 12 digit staff PIN")
    parser.add_argument("--packages", default="com.android.chrome")
    parser.add_argument("--start-url", default="")
    parser.add_argument("--allowlist", default="", help="Comma-separated Chrome patterns")
    parser.add_argument("--title", default="شوروم")
    parser.add_argument("--config-url", default="")
    parser.add_argument("--config-token", default="")
    parser.add_argument("--wifi-ssid", default="")
    parser.add_argument("--wifi-password", default="")
    parser.add_argument("--wifi-security", default="WPA", choices=["NONE", "WPA", "WEP"])
    parser.add_argument("--locale", default="fa_IR")
    parser.add_argument("--out", type=Path, default=root / "provisioning/out")
    args = parser.parse_args()

    if not args.pin.isdigit() or not 6 <= len(args.pin) <= 12:
        print("PIN must be 6 to 12 digits", file=sys.stderr)
        return 2
    if not args.url.startswith("https://"):
        print("APK url must be https", file=sys.stderr)
        return 2
    if not args.apk.is_file():
        print(f"APK not found: {args.apk}", file=sys.stderr)
        return 2

    props = load_props(root / "keystore.properties")
    keystore = root / props["storeFile"]
    signature = cert_checksum(keystore, props["keyAlias"], props["storePassword"])
    package_sum = checksum(args.apk.read_bytes())

    extras = {
        "allowed_packages": args.packages,
        "initial_pin": args.pin,
        "kiosk_enabled": "true",
        "showroom_title": args.title,
        "auto_launch": "true",
    }
    if args.start_url:
        extras["chrome_start_url"] = args.start_url
    if args.allowlist:
        extras["chrome_allowlist"] = args.allowlist
    if args.config_url:
        extras["config_url"] = args.config_url
    if args.config_token:
        extras["config_token"] = args.config_token

    payload: dict[str, object] = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": COMPONENT,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": args.url,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": signature,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM": package_sum,
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
        "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": False,
        "android.app.extra.PROVISIONING_LOCALE": args.locale,
        "android.app.extra.PROVISIONING_TIME_ZONE": "Asia/Tehran",
        "android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE": extras,
    }
    if args.wifi_ssid:
        payload["android.app.extra.PROVISIONING_WIFI_SSID"] = args.wifi_ssid
        payload["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"] = args.wifi_security
        payload["android.app.extra.PROVISIONING_WIFI_HIDDEN"] = False
        if args.wifi_password:
            payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"] = args.wifi_password

    text = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    args.out.mkdir(parents=True, exist_ok=True)
    json_path = args.out / "provisioning.json"
    json_path.write_text(text + "\n", encoding="utf-8")
    print(f"Wrote {json_path} ({len(text.encode('utf-8'))} bytes)")
    if len(text.encode("utf-8")) > 1800:
        print("QR payload is large. Shorten the title, allowlist, or token if the camera cannot scan it.", file=sys.stderr)

    try:
        import qrcode
    except ImportError:
        print("JSON is ready. Install qrcode to also write a PNG: pip install qrcode[pil]", file=sys.stderr)
        return 0
    image = qrcode.make(text)
    png_path = args.out / "provisioning.png"
    image.save(png_path)
    print(f"Wrote {png_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
