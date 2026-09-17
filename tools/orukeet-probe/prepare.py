#!/usr/bin/env python3
"""Prepare pinned public probe inputs on the host, outside Android and version control."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import urllib.request
import wave
import zipfile

ROOT = Path(__file__).resolve().parent


def verified(path, pin):
    if not path.is_file() or path.stat().st_size != pin['bytes']:
        return False
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest() == pin['sha256']


def prepare(cache):
    pins = json.loads((ROOT / 'pins.json').read_text())
    cache.mkdir(parents=True, exist_ok=True)
    for pin in pins.values():
        path = cache / pin['name']
        if not verified(path, pin):
            print('Downloading', pin['name'], flush=True)
            partial = path.with_suffix(path.suffix + '.partial')
            with urllib.request.urlopen(pin['url'], timeout=60) as source, partial.open('wb') as target:
                shutil.copyfileobj(source, target)
            if not verified(partial, pin):
                raise ValueError('Download checksum/size mismatch: ' + pin['name'])
            partial.replace(path)
        print('Verified', pin['name'], flush=True)

    manifest = json.loads((cache / pins['manifest']['name']).read_text())
    model = cache / 'model'
    model.mkdir(exist_ok=True)
    if not all(verified(model / entry['path'], entry) for entry in manifest['files']):
        # Explicit regular members only. Archive preparation runs on the host, never the phone.
        with tarfile.open(cache / pins['archive']['name'], 'r:bz2') as archive:
            for entry in manifest['files']:
                name = entry['path']
                if Path(name).name != name:
                    raise ValueError('Unexpected payload path')
                member = archive.getmember(manifest['extract_dir'] + '/' + name)
                if not member.isfile() or member.size != entry['bytes']:
                    raise ValueError('Unexpected archive member')
                target = model / (name + '.partial')
                with archive.extractfile(member) as source, target.open('wb') as output:
                    shutil.copyfileobj(source, output)
                if not verified(target, entry):
                    raise ValueError('Payload checksum mismatch: ' + name)
                target.replace(model / name)

    (ROOT / 'libs').mkdir(exist_ok=True)
    runtime = cache / pins['runtime']['name']
    destination = ROOT / 'libs' / runtime.name
    if not verified(destination, pins['runtime']):
        shutil.copyfile(runtime, destination)
    native = []
    with zipfile.ZipFile(runtime) as archive, tempfile.TemporaryDirectory() as scratch:
        for entry in archive.infolist():
            if not entry.filename.startswith('jni/') or not entry.filename.endswith('.so'):
                continue
            path = Path(scratch) / 'library.so'
            path.write_bytes(archive.read(entry))
            headers = subprocess.check_output(['readelf', '-lW', str(path)], text=True)
            alignment = [int(line.split()[-1], 16) for line in headers.splitlines() if line.strip().startswith('LOAD ')]
            if not alignment or min(alignment) < 16384:
                raise ValueError('Native alignment below 16 KiB: ' + entry.filename)
            native.append({'path': entry.filename, 'bytes': entry.file_size,
                           'compressed_bytes': entry.compress_size, 'load_alignment': alignment})
    with wave.open(str(cache / 'sample.wav')) as sample:
        fixture = {'channels': sample.getnchannels(), 'sample_rate': sample.getframerate(),
                   'sample_width': sample.getsampwidth(), 'frames': sample.getnframes()}
    assert fixture == {'channels': 1, 'sample_rate': 16000, 'sample_width': 2, 'frames': 118960}
    receipt = {'pins': pins, 'model_files': manifest['files'], 'fixture': fixture, 'native': native}
    (cache / 'preparation.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print('Verified seven model files and', len(native), 'native libraries; local AAR ready.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--cache', type=Path, default=Path.home() / '.cache/ownkey-orukeet-probe')
    prepare(parser.parse_args().cache)
