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
        assert not any(name.endswith('.onnx') for name in names), 'Weights bundled in APK'
        libraries = [name for name in names if name.startswith('lib/') and name.endswith('.so')]
        abis = {name.split('/')[1] for name in libraries}
        assert len(abis) == 1, 'Expected exactly one ABI'
        abi = next(iter(abis))
        for name in libraries:
            alignments = load_alignments(archive.read(name))
            assert alignments, 'No load segments'
            if abi in ('arm64-v8a','x86_64'):
                assert min(alignments) >= 16384, 'Unaligned native library: ' + name
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
