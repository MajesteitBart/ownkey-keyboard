# N-gram language model attribution

`en.bin` and `nl.bin` are static trigram language models (word list, casing flags, word validity flags and pruned,
quantised n-gram probabilities) built by `tools/lm/build.sh` (large profile). Format: `tools/lm/FORMAT.md`. Exact
sources, sizes and sampling: `tools/lm/SOURCES.md`. The files contain aggregated statistics only, no running text.

Data used: `nl.bin` from all Dutch OpenSubtitles v2018 lines (596M tokens) and 210M tokens of FineWeb-2 Dutch web
text; `en.bin` from every 5th English OpenSubtitles v2018 line (476M tokens) and 90M tokens of FineWeb English web
text.

## Sources

- **OpenSubtitles v2018 (OPUS)**, monolingual Dutch and English. Subtitles from http://www.opensubtitles.org/,
  collected by OPUS (https://opus.nlpl.eu/OpenSubtitles/). OPUS states no separate licence for this corpus and asks
  for a link to http://www.opensubtitles.org/ and this citation:
  P. Lison and J. Tiedemann, 2016, *OpenSubtitles2016: Extracting Large Parallel Corpora from Movie and TV
  Subtitles*. In Proceedings of LREC 2016.
- **FineWeb-2** (Dutch, `nld_Latn`) and **FineWeb** (English, `sample-10BT`) by Hugging Face, licensed under the
  Open Data Commons Attribution License (ODC-By) v1.0, https://opendatacommons.org/licenses/by/1-0/. Use is also
  subject to the Common Crawl terms of use, https://commoncrawl.org/terms-of-use.
  Datasets: https://huggingface.co/datasets/HuggingFaceFW/fineweb-2 and
  https://huggingface.co/datasets/HuggingFaceFW/fineweb.

## Word validity flags

The validity flags (one bit per vocabulary word: is it in a curated word list) are derived from:

- **OpenTaal Dutch word list** (`nl.bin`), https://github.com/OpenTaal/opentaal-wordlist, version 2.20.23, used
  under the Revised BSD License (the list is also available under CC BY 3.0):

  > Copyright (c) 2020, OpenTaal. All rights reserved. © 2020 OpenTaal (Simon Brouwer, Sander van Geloven),
  > © 2006 - 2011 OpenTaal (Ruud Baars, Simon Brouwer), © 2001 - 2005 Simon Brouwer and others,
  > © 1996 Nederlandstalige TeX Gebruikersgroep.
  >
  > Redistribution and use in source and binary forms, with or without modification, are permitted provided that
  > the following conditions are met: Redistributions of source code must retain the above copyright notice, this
  > list of conditions and the following disclaimer. Redistributions in binary form must reproduce the above
  > copyright notice, this list of conditions and the following disclaimer in the documentation and/or other
  > materials provided with the distribution. Neither the name of the copyright holder nor the names of its
  > contributors may be used to endorse or promote products derived from this software without specific prior
  > written permission.
  >
  > THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
  > WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
  > PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
  > DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  > PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
  > CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
  > OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
  > DAMAGE.

- **SCOWL / English Speller Database (ESDB)** (`en.bin`), https://github.com/en-wl/wordlist, a size-70 US and GB
  list generated at https://app.aspell.net/create (ESDB revision 1e5b7d3):

  > Copyright 2000-2026 by Kevin Atkinson
  >
  > Permission to use, copy, modify, distribute, and sell any part of the English Speller Database (ESDB,
  > previously known as SCOWLv2), or word lists created from it, is hereby granted without fee, provided that the
  > above copyright notice appears in all copies and that both the above copyright notice and this notice appear
  > in supporting documentation. Kevin Atkinson makes no representations about the suitability of this database
  > for any purpose. It is provided "as is" without express or implied warranty.

Keep this attribution when redistributing these files.
