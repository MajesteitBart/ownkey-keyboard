import importlib.util
from pathlib import Path
import unittest
import subprocess
import sys
import tempfile
import zipfile
import struct


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


class EvidenceTest(unittest.TestCase):
    def test_packaging_guards_survive_python_optimization(self):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'weights.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('assets/model.onnx', b'not allowed')
            result = subprocess.run([sys.executable, '-O', str(Path(__file__).with_name('check-packages.py')),
                                     '--zipalign', 'must-not-run', str(apk)], capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('Weights bundled in APK', result.stderr)

    def test_packaging_rejects_wrong_elf_architecture(self):
        checker = module('check-packages')
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'wrong.apk'
            header = bytearray(64)
            header[:6] = b'\x7fELF\x02\x01'
            struct.pack_into('<H', header, 18, 62)  # x86_64 in an arm64 directory
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('lib/arm64-v8a/libtest.so', header)
            with self.assertRaisesRegex(ValueError, 'architecture'):
                checker.inspect(apk, 'must-not-run')

    def test_latency_metadata_and_even_median(self):
        latency = module('compare-latency')
        self.assertEqual(latency.quantile([1, 3, 7, 9], .5), 5)
        with self.assertRaisesRegex(ValueError, 'schema'):
            latency.compare({}, {})
        with self.assertRaisesRegex(ValueError, 'Metric'):
            latency.compare({'schema':2,'metric':'one'}, {'schema':2,'metric':'two'})

    def test_wer_and_silence_are_separate(self):
        scorer = module('score')
        result = scorer.score([
            {'id':'1','language':'nl','subset':'general','reference':'Dit is goed.','hypothesis':'DIT is fout'},
            {'id':'2','language':'nl','subset':'silence','reference':'','hypothesis':'hallucination'},
        ])
        self.assertEqual(result['nl/general']['edits'],1)
        self.assertEqual(result['nl/general']['wer'],1/3)
        self.assertEqual(result['nl/general']['reference_characters'],9)
        self.assertEqual(result['nl/general']['cer'],3/9)
        self.assertEqual(result['nl/silence']['silence_false_positives'],1)
        self.assertIsNone(result['nl/silence']['wer'])
    def test_latency_pass_and_fail(self):
        compare = module('compare-latency').compare
        def data(n):
            return {'schema':2,'metric':'touch to pre-draw','api':36,'abi':'arm64-v8a','rounds_ms':[[n]*100 for _ in range(20)],'keyboard_open_rounds_ms':[[n]*10 for _ in range(20)]}
        self.assertEqual(compare(data(100),data(104))['typing_p95']['gate'],'pass')
        self.assertEqual(compare(data(100),data(110))['typing_p95']['gate'],'fail')
        self.assertEqual(compare(data(100),data(110))['keyboard_open_p95']['gate'],'fail')
        with self.assertRaises(ValueError):
            compare(data(0),data(100))
        short = data(100)
        short['keyboard_open_rounds_ms'] = [[100] for _ in range(20)]
        with self.assertRaises(ValueError):
            compare(short, data(100))


if __name__ == '__main__': unittest.main()
