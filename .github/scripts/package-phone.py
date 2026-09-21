#!/usr/bin/env python3
"""Package all ABI APKs using AGP metadata, with arm64 as the rolling debug download."""
import argparse
import json
import re
import shutil
from pathlib import Path


def package(source, destination, build_type, version, sha):
    for label in (build_type, version, sha):
        if not re.fullmatch(r'[A-Za-z0-9.+_-]+', label):
            raise ValueError('Unsafe artifact label')
    metadata = json.loads((source / 'output-metadata.json').read_text())
    entries = {}
    for element in metadata['elements']:
        abis = [item['value'] for item in element['filters'] if item['filterType'] == 'ABI']
        if len(abis) != 1 or abis[0] in entries:
            raise ValueError('Expected one unique ABI per APK; universal APKs are disabled')
        name = element['outputFile']
        if Path(name).name != name:
            raise ValueError('Unsafe APK path')
        entries[abis[0]] = source / name
    if set(entries) != {'arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'}:
        raise ValueError('Incomplete ABI artifacts')
    destination.mkdir(parents=True, exist_ok=True)
    result = {}
    for abi, original in sorted(entries.items()):
        target = destination / f'ownkey-phone-{build_type}-{abi}-v{version}-{sha}.apk'
        shutil.copyfile(original, target)
        result[abi] = target
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--build-type', required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--sha', required=True)
    args = parser.parse_args()
    outputs = package(Path('app/build/outputs/apk') / args.build_type, Path('dist/phone'), args.build_type, args.version, args.sha)
    print('app_apk=' + str(outputs['arm64-v8a']))
