#!/usr/bin/env python3
"""Install, provision and run the public-fixture matrix on a dedicated test guest.

Detach any T3 Device panel without shutting the guest down before using this ADB driver.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import urllib.parse

from prepare import ROOT, verified

PACKAGE = 'nl.bartvandermeeren.ownkey.probe'
SCENARIOS = [
    ('normal', 'wav', ''), ('normal', 'aac', ''),
    ('isolated', 'wav', ''), ('isolated', 'aac', ''),
    ('normal', 'wav', 'load'), ('normal', 'wav', 'inference'),
    ('normal', 'wav', 'cancel'), ('isolated', 'wav', 'oom'),
    ('normal', 'wav', ''),  # Explicit retry after failures, in the same main process.
]


def run(args):
    base = ['adb', '-s', args.serial]

    def adb(*command, **kwargs):
        return subprocess.run(base + list(command), check=True, **kwargs)

    def shell(*command):
        return adb('shell', *command, capture_output=True, text=True).stdout.strip()

    def results():
        output = subprocess.run(base + ['exec-out', 'run-as', PACKAGE, 'cat', 'files/results.jsonl'],
                                capture_output=True, text=True)
        if output.returncode:
            return []
        return [json.loads(line) for line in output.stdout.splitlines() if line]

    pins = json.loads((ROOT / 'pins.json').read_text())
    if not (verified(args.cache / 'manifest.json', pins['manifest'])):
        raise ValueError('Run prepare.py first')
    manifest = json.loads((args.cache / 'manifest.json').read_text())
    payloads = [(args.cache / 'model' / entry['path'], 'model/' + entry['path'], entry)
                for entry in manifest['files']]
    payloads.append((args.cache / 'sample.wav', 'sample.wav', pins['fixture']))
    for path, _, pin in payloads:
        if not (verified(path, pin)):
            raise ValueError('Unverified input: ' + path.name)
    apk = args.apk or next((ROOT / 'build/outputs/apk/debug').glob('*.apk'))
    adb('install', '-r', str(apk))
    adb('shell', 'am', 'force-stop', PACKAGE, stdout=subprocess.DEVNULL)
    adb('shell', 'run-as', PACKAGE, 'mkdir', '-p', 'files/probe-data/model')
    for path, relative, _ in payloads:
        destination = 'files/probe-data/' + relative
        with path.open('rb') as source:
            adb('exec-in', 'run-as', PACKAGE, 'sh', '-c', 'cat > ' + destination, stdin=source)
    metadata = {'api': shell('getprop', 'ro.build.version.sdk'), 'abi': shell('getprop', 'ro.product.cpu.abi'),
                'fingerprint': shell('getprop', 'ro.build.fingerprint'),
                'page_bytes': int(shell('getconf', 'PAGE_SIZE')), 'scenarios': []}
    with apk.open('rb') as source:
        metadata['apk_sha256'] = hashlib.file_digest(source, 'sha256').hexdigest()
    metadata['source_sha256'] = {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                                 for path in sorted(ROOT.glob('src/main/**/*')) if path.is_file()}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    original_main_pid = None
    for mode, audio_format, failure in SCENARIOS:
        count = len(results())
        query = urllib.parse.urlencode({'mode': mode, 'format': audio_format, 'failure': failure})
        # am's URL is quoted for the Android shell, not interpolated through a host shell.
        shell('am', 'start', '-a', 'android.intent.action.VIEW', '-d', "'ownkey-probe://run?" + query + "'")
        deadline = time.monotonic() + 130
        while time.monotonic() < deadline:
            current = results()
            if len(current) > count:
                result = current[-1]
                break
            time.sleep(0.5)
        else:
            raise TimeoutError('No result for ' + query)
        if not (result['mode'] == mode and result['requested_failure'] == failure):
            raise ValueError('Unexpected result')
        if result.get('sample_format') != audio_format:
            raise ValueError('Unexpected decoder format')
        if original_main_pid is None:
            original_main_pid = result['main_pid']
        if result['main_pid'] != original_main_pid:
            raise ValueError('Main process restarted during the probe')
        pid = result.get('service_pid')
        if pid:
            # Binder death and an absent process are separate observations.
            death_deadline = time.monotonic() + 3
            while time.monotonic() < death_deadline:
                live = shell('ps', '-A', '-o', 'PID').split()
                if str(pid) not in live:
                    break
                time.sleep(0.1)
            result['service_absent_after_run'] = str(pid) not in live
        result['main_pid_still_running'] = str(result['main_pid']) in shell('pidof', PACKAGE).split()
        metadata['scenarios'].append(result)
        args.output.write_text(json.dumps(metadata, indent=2) + '\n')
        print(mode, audio_format, failure or 'success', result['status'], flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--cache', type=Path, default=Path.home() / '.cache/ownkey-orukeet-probe')
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--output', type=Path, default=ROOT / 'evidence/results.json')
    run(parser.parse_args())
