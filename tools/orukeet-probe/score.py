#!/usr/bin/env python3
"""Local WER aggregation. Input JSONL: id, language, subset, reference, hypothesis.

Keep consented/raw input outside the repository. Output contains aggregate counts only.
"""
import argparse
from collections import defaultdict
import json
import unicodedata


def words(text):
    text = unicodedata.normalize('NFKC', text).casefold()
    return ''.join(c if c.isalnum() or c.isspace() else ' ' for c in text).split()


def distance(a, b):
    previous = list(range(len(b) + 1))
    for i, left in enumerate(a, 1):
        current = [i]
        for j, right in enumerate(b, 1):
            current.append(min(current[-1] + 1, previous[j] + 1, previous[j - 1] + (left != right)))
        previous = current
    return previous[-1]


def score(rows):
    groups = defaultdict(lambda: {'phrases': 0, 'reference_words': 0, 'edits': 0, 'reference_characters': 0, 'character_edits': 0, 'silence_false_positives': 0})
    seen = set()
    for row in rows:
        if row['id'] in seen:
            raise ValueError('Duplicate phrase id')
        seen.add(row['id'])
        if row['language'] not in ('nl', 'en') or row['subset'] not in ('general', 'names', 'numbers', 'punctuation', 'accent', 'noise', 'mixed', 'silence'):
            raise ValueError('Unknown corpus group')
        reference, hypothesis = words(row['reference']), words(row['hypothesis'])
        for group in (row['language'], row['language'] + '/' + row['subset']):
            record = groups[group]
            record['phrases'] += 1
            record['reference_words'] += len(reference)
            record['edits'] += distance(reference, hypothesis)
            record['reference_characters'] += len(''.join(reference))
            record['character_edits'] += distance(''.join(reference), ''.join(hypothesis))
            record['silence_false_positives'] += int(not reference and bool(hypothesis))
    for record in groups.values():
        record['wer'] = record['edits'] / record['reference_words'] if record['reference_words'] else None
        record['cer'] = record['character_edits'] / record['reference_characters'] if record['reference_characters'] else None
    return dict(sorted(groups.items()))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('input', type=argparse.FileType())
    args = parser.parse_args()
    print(json.dumps({'normalization': 'NFKC, casefold, punctuation to spaces; no number/name expansion', 'groups': score(json.loads(line) for line in args.input if line.strip())}, indent=2))
