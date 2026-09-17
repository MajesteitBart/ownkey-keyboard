import importlib.util
from pathlib import Path
import unittest


def module(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + '.py'))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


class EvidenceTest(unittest.TestCase):
    def test_wer_and_silence_are_separate(self):
        scorer = module('score')
        result = scorer.score([
            {'id':'1','language':'nl','subset':'general','reference':'Dit is goed.','hypothesis':'DIT is fout'},
            {'id':'2','language':'nl','subset':'silence','reference':'','hypothesis':'hallucination'},
        ])
        self.assertEqual(result['nl/general']['edits'],1)
        self.assertEqual(result['nl/general']['wer'],1/3)
        self.assertEqual(result['nl/silence']['silence_false_positives'],1)
        self.assertIsNone(result['nl/silence']['wer'])
    def test_latency_pass_and_fail(self):
        compare = module('compare-latency').compare
        def data(n):
            return {'api':36,'abi':'arm64-v8a','rounds_ms':[[n]*100 for _ in range(20)],'keyboard_open_rounds_ms':[[n]*10 for _ in range(20)]}
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
