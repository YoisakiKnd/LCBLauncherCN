#!/usr/bin/env python3
"""Verify a local debug APK without installing it or contacting a device."""
import hashlib
import os
from pathlib import Path
import struct
import subprocess
import sys
import zipfile
from apk_metadata import sdk_levels

project = Path(__file__).resolve().parent.parent
apk = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else project / "app/build/outputs/apk/debug/app-debug.apk"
sdk = Path(os.environ.get("ANDROID_HOME", project / ".tools/android-sdk"))
tools = sdk / "build-tools/35.0.0"
environment = os.environ.copy()
if (project / ".tools/jdk21").is_dir():
    environment["JAVA_HOME"] = str(project / ".tools/jdk21")


def run(*args):
    return subprocess.run([str(arg) for arg in args], env=environment, check=True, text=True, capture_output=True).stdout


print(run(tools / "apksigner", "verify", "--verbose", "--print-certs", apk))
run(tools / "zipalign", "-c", "-P", "16", "4", apk)
print("APK ZIP alignment: 16 KiB check passed")
badging = run(tools / "aapt2", "dump", "badging", apk)
assert "package: name='io.github.lcblauncher'" in badging, "Unexpected application ID"
manifest = run(tools / "aapt2", "dump", "xmltree", apk, "--file", "AndroidManifest.xml")
minimum, target = sdk_levels(manifest)
print(f"Compiled manifest SDK range: min={minimum}, target={target}")
assert (minimum, target) == (26, 36), "Unexpected SDK range"
permissions = run(tools / "aapt2", "dump", "permissions", apk)
for permission in ["INTERNET", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE",
                   "QUERY_ALL_PACKAGES", "REQUEST_INSTALL_PACKAGES"]:
    assert "android.permission." + permission not in permissions, f"Unexpected permission: {permission}"
print("Package, SDK and restricted-permission checks passed")

with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if not name.startswith("lib/arm64-v8a/") or not name.endswith(".so"):
            continue
        data = archive.read(name)
        assert data[:5] == b"\x7fELF\x02", "Expected ELF64"
        order = "<" if data[5] == 1 else ">"
        offset = struct.unpack_from(order + "Q", data, 32)[0]
        stride, count = struct.unpack_from(order + "HH", data, 54)
        alignment = []
        for index in range(count):
            header = offset + index * stride
            if struct.unpack_from(order + "I", data, header)[0] == 1:
                alignment.append(struct.unpack_from(order + "Q", data, header + 48)[0])
        assert alignment and min(alignment) >= 16384, f"ARM64 ELF alignment failed: {name}"
        print(f"ARM64 ELF alignment passed: {name}")

with apk.open("rb") as stream:
    print(f"SHA-256: {hashlib.file_digest(stream, 'sha256').hexdigest()}")
print(f"Size: {apk.stat().st_size} bytes")
