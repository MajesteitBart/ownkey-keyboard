#!/usr/bin/env python3
"""Prepare immutable per-file release assets; publication is a separate maintainer action."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path
from prepare import verified

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--cache', type=Path, default=Path.home() / '.cache/ownkey-orukeet-probe')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
pins = json.loads((Path(__file__).parent / 'pins.json').read_text())
if not (verified(args.cache / 'manifest.json', pins['manifest'])):
    raise ValueError('Probe integrity validation failed')
manifest = json.loads((args.cache / 'manifest.json').read_text())
args.output.mkdir(parents=True, exist_ok=True)
for entry in manifest['files']:
    name = entry['path']
    if not (Path(name).name == name and verified(args.cache / 'model' / name, entry)):
        raise ValueError('Probe integrity validation failed')
    shutil.copyfile(args.cache / 'model' / name, args.output / name)
shutil.copyfile(args.cache / 'manifest.json', args.output / 'upstream-manifest.json')
(args.output / 'provenance.json').write_text(json.dumps({
    'release': 'orukeet-v0.1.0-int8',
    'upstream_repository': 'oruk/orukeet',
    'upstream_revision': '55a984d46f68323301837194ce647c702f55facc',
    'archive': pins['archive'], 'manifest': pins['manifest'],
    'transformation': 'Regular files extracted unchanged on the build host; no re-export or quantization.',
    'files': manifest['files'],
}, indent=2) + '\n')
files = [args.output / e['path'] for e in manifest['files']] + [args.output / 'upstream-manifest.json', args.output / 'provenance.json']
checksums = []
for path in files:
    with path.open('rb') as stream:
        checksums.append(hashlib.file_digest(stream, 'sha256').hexdigest() + '  ' + path.name)
(args.output / 'SHA256SUMS').write_text('\n'.join(checksums) + '\n')
(args.output / 'release-notes.md').write_text('''Pinned Orukeet v0.1.0 INT8 model data for Ownkey internal testing.

Seven original model, tokenizer, licence and notice files, extracted unchanged from upstream revision `55a984d46f68323301837194ce647c702f55facc`. The Android downloader verifies their pinned sizes and SHA-256 hashes before offering activation. It downloads individual files; no archive extraction occurs on the phone.

- Weights terms: `LICENSE-WEIGHTS`; attribution and training/model notices: `NOTICE.md`.
- Provenance and upstream archive pin: `provenance.json` and `upstream-manifest.json`.
- Transfer checksums: `SHA256SUMS`.
- This prerelease contains model data only. Physical arm64 accuracy, memory, responsiveness and thermal qualification remain pending.

Assets are immutable: a changed model requires a new release identifier and new app catalog pins.
''')
print('Prepared seven verified payloads, provenance and checksums.')
