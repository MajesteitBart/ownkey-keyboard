#!/usr/bin/env python3
"""Compare paired physical-device benchmark rounds; bootstrap whole rounds, not individual taps."""
import argparse
import json
import random
import math


def quantile(values, q):
    ordered = sorted(values)
    position = (len(ordered)-1)*q
    lower, upper = math.floor(position), math.ceil(position)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def compare(baseline, candidate):
    if baseline.get('schema') != 2 or candidate.get('schema') != 2:
        raise ValueError('Expected benchmark schema 2')
    if not baseline.get('metric') or baseline['metric'] != candidate.get('metric'):
        raise ValueError('Metric definitions differ or are missing')
    if len(baseline['rounds_ms']) != len(candidate['rounds_ms']):
        raise ValueError('Round counts differ')
    count = len(baseline['rounds_ms'])
    if count < 20:
        raise ValueError('At least 20 paired rounds are required for a gate decision')
    if baseline['api'] != candidate['api'] or baseline['abi'] != candidate['abi']:
        raise ValueError('Device configurations differ')
    randomizer = random.Random(7311)
    results = {}
    for metric in ('typing_p50', 'typing_p95', 'keyboard_open_p95'):
        key = 'keyboard_open_rounds_ms' if metric.startswith('keyboard') else 'rounds_ms'
        base, changed = baseline.get(key, []), candidate.get(key, [])
        minimum = 10 if metric.startswith('keyboard') else 100
        if len(base) != count or len(changed) != count:
            raise ValueError('Missing paired rounds')
        if any(len(row) < minimum or any(not math.isfinite(v) or v <= 0 for v in row)
               for row in base + changed):
            raise ValueError('Insufficient or invalid samples')
        if any(len(a) != len(b) for a,b in zip(base, changed)):
            raise ValueError('Paired sample counts differ')
        percentile = .5 if metric.endswith('50') else .95
        def estimate(indices):
            before = quantile([v for i in indices for v in base[i]], percentile)
            after = quantile([v for i in indices for v in changed[i]], percentile)
            return (after / before - 1) * 100
        observed = estimate(range(count))
        samples = [estimate(randomizer.choices(range(count), k=count)) for _ in range(10000)]
        low, high = quantile(samples, .025), quantile(samples, .975)
        results[metric] = {'regression_percent': observed, 'ci95_percent': [low, high],
                           'gate': 'pass' if high <= 5 else 'fail' if low > 5 else 'inconclusive'}
    return results


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('baseline', type=argparse.FileType())
    parser.add_argument('candidate', type=argparse.FileType())
    args = parser.parse_args()
    print(json.dumps(compare(json.load(args.baseline), json.load(args.candidate)),indent=2))
