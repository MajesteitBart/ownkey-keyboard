#!/usr/bin/env python3
"""Build sherpa JNI without TTS/GPL dependencies. Inputs and transitive CMake downloads are pinned."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parent


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def acquire(cache, pin):
    target = cache / pin['name']
    def valid():
        return target.is_file() and target.stat().st_size == pin['bytes'] and digest(target) == pin['sha256']
    if not valid():
        temporary = target.with_suffix('.partial')
        with urllib.request.urlopen(pin['url'], timeout=60) as source, temporary.open('wb') as output:
            shutil.copyfileobj(source, output)
        if temporary.stat().st_size != pin['bytes'] or digest(temporary) != pin['sha256']:
            raise ValueError('Input digest mismatch: ' + pin['name'])
        temporary.replace(target)
    return target


def extract(archive, directory):
    existing = directory.exists()
    directory.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as source:
        for entry in source.infolist():
            if entry.filename.startswith('/') or '..' in Path(entry.filename).parts:
                raise ValueError('Unsafe archive member')
            if existing and not entry.is_dir():
                local = directory / entry.filename
                if not local.is_file() or local.read_bytes() != source.read(entry):
                    raise ValueError('Extracted input changed; use a clean cache: ' + entry.filename)
        if not existing:
            source.extractall(directory)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--cache', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--jobs', type=int, default=8)
    args = parser.parse_args()
    pins = json.loads((ROOT / 'pins.json').read_text())
    cache = args.cache.resolve(); cache.mkdir(parents=True, exist_ok=True)
    args.output.mkdir(parents=True, exist_ok=True)
    source_zip = acquire(cache, pins['source'])
    ort_zip = acquire(cache, pins['ort'])
    bindings = acquire(cache, pins['bindings'])
    extract(source_zip, cache / 'source')
    extract(ort_zip, cache / 'ort-android')
    source = cache / 'source/sherpa-onnx-1.13.4'
    sdk = args.sdk.resolve()
    cmake = sdk / f"cmake/{pins['cmake']}/bin/cmake"
    ndk = sdk / 'ndk' / pins['ndk']
    shutil.copyfile(ndk/'NOTICE.toolchain', args.output/'NDK-NOTICES.txt')
    strip = ndk / 'toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip'
    off = ('TTS','SPEAKER_DIARIZATION','BINARY','C_API','WEBSOCKET','PORTAUDIO','PYTHON','TESTS','CHECK')
    entries = {}
    with zipfile.ZipFile(bindings) as original:
        # Never copy the upstream native libraries; the AAR includes unused GPL TTS code.
        for name in ('AndroidManifest.xml','classes.jar','R.txt'):
            entries[name] = original.read(name)
    binaries = []
    for abi in pins['abis']:
        build = cache / 'asr-build' / abi
        env = dict(os.environ, SHERPA_ONNXRUNTIME_LIB_DIR=str(cache/'ort-android/jni'/abi),
                   SHERPA_ONNXRUNTIME_INCLUDE_DIR=str(cache/'ort-android/headers'))
        subprocess.run([str(cmake),'-S',str(source),'-B',str(build),'-G','Ninja',
            '-DCMAKE_MAKE_PROGRAM='+str(cmake.with_name('ninja')),
            '-DCMAKE_TOOLCHAIN_FILE='+str(ndk/'build/cmake/android.toolchain.cmake'),
            '-DANDROID_ABI='+abi,'-DANDROID_PLATFORM=android-26','-DCMAKE_BUILD_TYPE=Release',
            '-DBUILD_SHARED_LIBS=ON','-DSHERPA_ONNX_ENABLE_JNI=ON',
            '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384',
            '-DCMAKE_C_FLAGS=-ffile-prefix-map='+str(source)+'=sherpa-onnx',
            '-DCMAKE_CXX_FLAGS=-DEIGEN_MPL2_ONLY -ffile-prefix-map='+str(source)+'=sherpa-onnx',
            *['-DSHERPA_ONNX_ENABLE_'+feature+'=OFF' for feature in off]],env=env,check=True)
        subprocess.run([str(cmake),'--build',str(build),'--target','sherpa-onnx-jni','--parallel',str(args.jobs)],env=env,check=True)
        assert not any((build/'_deps').glob('*espeak*')) and not any((build/'_deps').glob('*piper*'))
        candidates = list(build.rglob('libsherpa-onnx-jni.so'))
        assert len(candidates) == 1
        temporary = args.output / f'{abi}-jni.so'
        shutil.copyfile(candidates[0], temporary)
        subprocess.run([str(strip),'--strip-unneeded',str(temporary)],check=True)
        data = temporary.read_bytes(); temporary.unlink()
        # ASR-only configuration must not carry eSpeak implementation strings.
        assert b'ESPEAK_DATA_PATH' not in data and b'espeak-ng-data' not in data
        entries[f'jni/{abi}/libsherpa-onnx-jni.so'] = data
        entries[f'jni/{abi}/libonnxruntime.so'] = (cache/'ort-android/jni'/abi/'libonnxruntime.so').read_bytes()
        binaries.append({'abi':abi,'jni_sha256':hashlib.sha256(data).hexdigest()})
    # Full licence texts for the actual native dependency closure, including file-level MPL notices.
    dependency_root = cache / 'asr-build' / pins['abis'][0] / '_deps'
    notices = ['Ownkey ASR-only sherpa-onnx 1.13.4; TTS, diarization and executables disabled.\nEigen is compiled with EIGEN_MPL2_ONLY.\n']
    for dependency in sorted(dependency_root.glob('*-src')):
        for path in sorted(dependency.iterdir()):
            if path.is_file() and path.name.upper().startswith(('LICENSE','COPYING','NOTICE')):
                notices.append('\n## '+dependency.name+'/'+path.name+'\n\n'+path.read_text(errors='replace'))
            elif path.is_dir() and path.name == 'LICENSES':
                for license_file in sorted(path.glob('*.txt')):
                    notices.append('\n## '+dependency.name+'/LICENSES/'+license_file.name+'\n\n'+license_file.read_text())
    notice = '\n'.join(notices).encode()
    (args.output/'THIRD-PARTY-NOTICES.txt').write_bytes(notice)
    entries['assets/ownkey-asr/THIRD-PARTY-NOTICES.txt'] = notice
    entries['assets/ownkey-asr/LICENSE'] = (source/'LICENSE').read_bytes()
    aar = args.output/'ownkey-sherpa-onnx-1.13.4-asr1.aar'
    with zipfile.ZipFile(aar,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as output:
        for name,data in sorted(entries.items()):
            info = zipfile.ZipInfo(name,date_time=(2026,7,7,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            output.writestr(info,data)
    receipt = {'name':aar.name,'bytes':aar.stat().st_size,'sha256':digest(aar),'inputs':pins,
               'features_disabled':list(off),'eigen_mpl2_only':True,'binaries':binaries}
    (args.output/'provenance.json').write_text(json.dumps(receipt,indent=2)+'\n')
    for name in ('build.py','pins.json'):
        shutil.copyfile(ROOT/name,args.output/name)
    print(json.dumps(receipt),flush=True)


if __name__ == '__main__': main()
