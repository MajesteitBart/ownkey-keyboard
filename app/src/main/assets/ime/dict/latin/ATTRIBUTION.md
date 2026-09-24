# Latin dictionaries attribution

`en.txt` and `nl.txt` are built by `tools/dictionary-build/build.py`, and `en.bigrams.txt` and `nl.bigrams.txt`
by `tools/dictionary-build/bigrams.py`, from these sources:

| Source | Used for | License |
| --- | --- | --- |
| [FrequencyWords](https://github.com/hermitdave/FrequencyWords) 2018 `en_50k` and `nl_50k` (Hermit Dave) | Word frequencies | CC BY-SA 4.0 |
| [SCOWL](http://wordlist.aspell.net) 2020.12.07, sizes 10 to 70, names 10 to 95 (Kevin Atkinson and contributors) | Which English words are valid | SCOWL notice license, full text in the app's third-party licenses |
| [OpenTaal word list](https://github.com/OpenTaal/opentaal-wordlist) (Stichting OpenTaal) | Which Dutch words are valid | BSD-3-Clause, or CC BY 3.0 at the user's choice |
| [Tatoeba](https://tatoeba.org) per-language sentence exports (Tatoeba contributors) | Word pair counts (bigrams); every tenth sentence is held out for testing | CC BY 2.0 FR |

Frequencies are stored as `round(100 * ln(count))`. English contraction counts are estimated from the split
tokens in the FrequencyWords list; Dutch elision counts from the split letters and, for `zo'n`, from Tatoeba
sentences. The shared adaptation of the FrequencyWords data is available under CC BY-SA 4.0.
