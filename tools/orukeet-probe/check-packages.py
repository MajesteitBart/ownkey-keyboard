#!/usr/bin/env python3
"""Verify ABI-specific APKs, absence of model weights, and 16 KB ELF/ZIP alignment."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import subprocess
import zipfile


def load_alignments(data):
    if data[:4] != b'\x7fELF':
        raise ValueError('Not ELF')
    endian = '<' if data[5] == 1 else '>'
    is64 = data[4] == 2
    offset = struct.unpack_from(endian + ('Q' if is64 else 'I'), data, 32 if is64 else 28)[0]
    size, count = struct.unpack_from(endian + 'HH', data, 54 if is64 else 42)
    return [struct.unpack_from(endian + ('Q' if is64 else 'I'), data, offset+i*size+(48 if is64 else 28))[0]
            for i in range(count) if struct.unpack_from(endian+'I',data,offset+i*size)[0] == 1]


def inspect(path, zipalign):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if not (not any(name.endswith('.onnx') for name in names)):
            raise ValueError('Weights bundled in APK')
        libraries = [name for name in names if name.startswith('lib/') and name.endswith('.so')]
        abis = {name.split('/')[1] for name in libraries}
        if not (len(abis) == 1):
            raise ValueError('Expected exactly one ABI')
        abi = next(iter(abis))
        abi_headers = {'arm64-v8a': (2, 183), 'x86_64': (2, 62), 'armeabi-v7a': (1, 40), 'x86': (1, 3)}
        if abi not in abi_headers:
            raise ValueError('Unknown ABI')
        for name in libraries:
            data = archive.read(name)
            if len(data) < 20 or data[5] != 1 or (data[4], struct.unpack_from('<H', data, 18)[0]) != abi_headers[abi]:
                raise ValueError('ELF architecture does not match APK ABI')
            alignments = load_alignments(data)
            if not (alignments):
                raise ValueError('No load segments')
            if abi in ('arm64-v8a','x86_64'):
                if not (min(alignments) >= 16384):
                    raise ValueError('Unaligned native library: ' + name)
    subprocess.run([zipalign,'-c','-P','16','4',str(path)], check=True, capture_output=True)
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream,'sha256').hexdigest()
    return {'apk':path.name,'abi':abi,'bytes':path.stat().st_size,'native_libraries':len(libraries),'sha256':digest,'zip_alignment':'pass','elf_16k':'pass' if abi in ('arm64-v8a','x86_64') else 'not-applicable'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--zipalign',required=True)
    parser.add_argument('apks',type=Path,nargs='+')
    args = parser.parse_args()
    print(json.dumps([inspect(path,args.zipalign) for path in args.apks],indent=2))
