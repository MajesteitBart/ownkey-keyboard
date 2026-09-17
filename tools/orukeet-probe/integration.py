#!/usr/bin/env python3
"""Provision verified public inputs and run the app's opt-in lifecycle test on an explicit serial.

Close the collaborative Device panel first. Never runs against an implicitly selected device.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path
from prepare import verified

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'nl.bartvandermeeren.ownkey.debug'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--cache', type=Path, default=Path.home() / '.cache/ownkey-orukeet-probe')
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--soak-seconds', type=int, default=0)
    args = parser.parse_args()
    pins = json.loads((Path(__file__).parent / 'pins.json').read_text())
    assert verified(args.cache / 'manifest.json', pins['manifest'])
    manifest = json.loads((args.cache / 'manifest.json').read_text())
    assert all(verified(args.cache / 'model' / x['path'], x) for x in manifest['files'])
    assert verified(args.cache / 'sample.wav', pins['fixture'])
    def adb(*command, **kwargs):
        return subprocess.run([args.adb, '-s', args.serial, *command], check=True, **kwargs)
    abi = adb('shell', 'getprop', 'ro.product.cpu.abi', capture_output=True, text=True).stdout.strip()
    assert abi in ('x86_64', 'arm64-v8a')
    adb('install', '-r', str(ROOT / f'app/build/outputs/apk/debug/app-{abi}-debug.apk'))
    adb('install', '-r', str(ROOT / 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    adb('shell', 'am', 'force-stop', PACKAGE)
    model = 'no_backup/ai-models/models/orukeet-v0.1.0-int8'
    adb('shell', 'run-as', PACKAGE, 'mkdir', '-p', model, 'no_backup/orukeet-test')
    for entry in manifest['files']:
        name = entry['path']
        assert Path(name).name == name
        with (args.cache / 'model' / name).open('rb') as source:
            adb('exec-in', 'run-as', PACKAGE, 'sh', '-c', f'cat > {model}/{name}', stdin=source)
    with (args.cache / 'sample.wav').open('rb') as source:
        adb('exec-in', 'run-as', PACKAGE, 'sh', '-c', 'cat > no_backup/orukeet-test/sample.wav', stdin=source)
    runtime_sha = re.search(r'val sherpaDigest = "([0-9a-f]{64})"', (ROOT/'lib/offline-asr/build.gradle.kts').read_text()).group(1)
    result = adb('shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
        'dev.patrickgold.florisboard.OrukeetIntegrationTest#nativeLifecycleAndCrashRecovery',
        '-e', 'soakSeconds', str(args.soak_seconds), '-e', 'runtimeSha256', runtime_sha,
        PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', capture_output=True, text=True)
    print(result.stdout)
    if 'OK (1 test)' not in result.stdout or 'INSTRUMENTATION_STATUS_CODE: 0' not in result.stdout:
        raise RuntimeError('Lifecycle test did not execute and pass')


if __name__ == '__main__':
    main()
