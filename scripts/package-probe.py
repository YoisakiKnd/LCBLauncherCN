#!/usr/bin/env python3
"""Package an already built and verified debug APK; never invokes build tools."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import zipfile

root = Path(__file__).resolve().parent.parent
output = root / 'app/build/outputs/apk/debug'
metadata = json.loads((output / 'output-metadata.json').read_text())
if metadata['applicationId'] != 'io.github.lcblauncher' or metadata['variantName'] != 'debug':
    raise SystemExit('Unexpected APK build metadata')
if len(metadata['elements']) != 1:
    raise SystemExit('Expected one universal debug APK')
element = metadata['elements'][0]
destination = root / 'dist'
destination.mkdir(exist_ok=True)
apk = destination / f"LCBLauncher-{element['versionName']}-debug.apk"
shutil.copyfile(output / element['outputFile'], apk)
shutil.copyfile(root / 'docs/testing-vivo-android16.md', destination / 'PHONE-TEST.md')
shutil.copyfile(root / 'apk-verification.txt', destination / 'APK-VERIFICATION.txt')
sample = destination / 'LCBLauncher-audit-sample.zip'
with zipfile.ZipFile(sample, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    path = root / 'fixtures/archive-sample/SAMPLE_NOT_FOR_GAME.json'
    archive.write(path, path.name)
(destination / 'build-info.json').write_text(json.dumps({
    'versionName': element['versionName'],
    'versionCode': element['versionCode'],
    'commit': os.environ.get('GITHUB_SHA'),
    'runId': os.environ.get('GITHUB_RUN_ID'),
    'translationImplemented': False,
    'signing': 'debug-test-only',
}, indent=2) + '\n')
checksums = []
for path in sorted(destination.iterdir()):
    if not path.is_file() or path.name == 'SHA256SUMS.txt':
        continue
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    checksums.append(f'{digest}  {path.name}')
(destination / 'SHA256SUMS.txt').write_text('\n'.join(checksums) + '\n')
print(f'Packaged {apk.name}, phone instructions, sample ZIP and checksums')
