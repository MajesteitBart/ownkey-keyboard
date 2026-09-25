// Faithful JS port of LatinLanguageProvider suggest()/autocorrect scoring (Ownkey, Sep 2026),
// plus a reference noisy-channel scorer used only to estimate headroom.
const fs = require('fs');

const norm = (w) => w.trim().replace(/’/g, "'").toLowerCase();
const cmpStr = (a, b) => (a < b ? -1 : a > b ? 1 : 0);
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

function loadFreq(path) {
  const words = new Map();
  for (const line of fs.readFileSync(path, 'utf8').split(/\r?\n/)) {
    const t = line.trim();
    if (!t) continue;
    const sep = Math.max(t.lastIndexOf(' '), t.lastIndexOf('\t'));
    if (sep <= 0 || sep >= t.length - 1) continue;
    const w = norm(t.slice(0, sep));
    if (!w) continue;
    const f = Number(t.slice(sep + 1));
    if (!Number.isInteger(f)) continue;
    const sf = Math.max(f, 1);
    if (sf > (words.get(w) || 0)) words.set(w, sf);
  }
  return words;
}

function deletes1(w) {
  const s = new Set();
  for (let i = 0; i < w.length; i++) s.add(w.slice(0, i) + w.slice(i + 1));
  return s;
}

function buildModel(words, { maxDeleteWords = 20000 } = {}) {
  const entries = [...words.entries()]
    .filter(([w]) => w.length >= 2 && w.length <= 18)
    .sort((a, b) => b[1] - a[1] || cmpStr(a[0], b[0]))
    .slice(0, maxDeleteWords);
  const del = new Map();
  for (const [w] of entries) {
    for (const d of deletes1(w)) {
      let l = del.get(d);
      if (!l) del.set(d, (l = []));
      l.push(w);
    }
  }
  const pre = new Map();
  for (const [w, f] of words) {
    const md = Math.min(3, w.length);
    for (let d = 1; d <= md; d++) {
      const p = w.slice(0, d);
      let l = pre.get(p);
      if (!l) pre.set(p, (l = []));
      l.push([w, f]);
    }
  }
  for (const [p, l] of pre) {
    l.sort((a, b) => b[1] - a[1] || cmpStr(a[0], b[0]));
    pre.set(p, l.slice(0, 48));
  }
  let max = 1;
  for (const f of words.values()) if (f > max) max = f;
  return { words, del, pre, max };
}

function osa(s, t, limit) {
  if (s === t) return 0;
  if (Math.abs(s.length - t.length) > limit) return limit + 1;
  let pp = new Array(t.length + 1).fill(0);
  let p = Array.from({ length: t.length + 1 }, (_, i) => i);
  let c = new Array(t.length + 1).fill(0);
  for (let i = 1; i <= s.length; i++) {
    c[0] = i;
    let rowMin = c[0];
    for (let j = 1; j <= t.length; j++) {
      const cost = s[i - 1] === t[j - 1] ? 0 : 1;
      let v = Math.min(p[j] + 1, c[j - 1] + 1, p[j - 1] + cost);
      if (i > 1 && j > 1 && s[i - 1] === t[j - 2] && s[i - 2] === t[j - 1]) v = Math.min(v, pp[j - 2] + 1);
      c[j] = v;
      if (v < rowMin) rowMin = v;
    }
    if (rowMin > limit) return limit + 1;
    [pp, p, c] = [p, c, pp];
  }
  return p[t.length];
}

function lookupPrefix(model, input, maxCount) {
  const md = Math.min(3, input.length);
  let bucket = null;
  for (let d = md; d >= 1; d--) {
    bucket = model.pre.get(input.slice(0, d));
    if (bucket) break;
  }
  if (!bucket) return [];
  const out = [];
  for (const [w, f] of bucket) {
    if (w !== input && w.startsWith(input)) out.push({ word: w, distance: 0, frequency: f, isPrefixMatch: true });
    if (out.length >= maxCount) break;
  }
  return out;
}

function lookupCorrections(model, input, maxCount) {
  const cands = new Set(model.del.get(input) || []);
  for (const d of deletes1(input)) {
    if (model.words.has(d)) cands.add(d);
    for (const w of model.del.get(d) || []) cands.add(w);
  }
  const ranked = [];
  for (const w of cands) {
    if (w === input) continue;
    const f = model.words.get(w);
    if (!f) continue;
    const dist = osa(input, w, 1);
    if (dist <= 1) ranked.push({ word: w, distance: dist, frequency: f, isPrefixMatch: w.startsWith(input) });
  }
  ranked.sort((a, b) => a.distance - b.distance || b.frequency - a.frequency || cmpStr(a.word, b.word));
  return ranked.slice(0, maxCount);
}

function rankScore(model, input, c) {
  const fs_ = c.frequency / model.max;
  const pb = c.isPrefixMatch ? 0.35 : 0;
  const dp = c.distance === 0 ? 0 : c.distance === 1 ? 0.2 : 0.5;
  const lp = (Math.abs(c.word.length - input.length) / Math.max(input.length, 1)) * 0.18;
  const sw = input.length >= 5 && c.word.length <= 3 ? 0.3 : 0;
  return fs_ + pb - dp - lp - sw;
}

function confidence(model, input, c) {
  const fs_ = clamp(c.frequency / model.max, 0, 1);
  const dp = c.distance === 0 ? 1.0 : c.distance === 1 ? 0.75 : 0.5;
  const pb = c.isPrefixMatch ? 1 : 0;
  const lc = clamp(1 - Math.abs(c.word.length - input.length) / Math.max(input.length, 1), 0, 1);
  return clamp(0.55 * fs_ + 0.25 * dp + 0.15 * pb + 0.05 * lc, 0.05, 1);
}

function languageWeights(langs, contextTokens, input) {
  const raw = {};
  for (const l of langs) {
    let ev = 0;
    contextTokens.forEach((tok, i) => {
      if (l.model.words.has(tok)) ev += 0.6 + 0.4 * ((i + 1) / Math.max(contextTokens.length, 1));
    });
    const exact = input != null && l.model.words.has(input);
    raw[l.lang] = Math.max((l.isPrimary ? 1.0 : 0.78) + 0.4 * ev + (exact ? 0.42 : 0), 0.01);
  }
  const sum = Math.max(Object.values(raw).reduce((a, b) => a + b, 0), 0.01);
  for (const k in raw) raw[k] /= sum;
  return raw;
}

const PROFILES = {
  DEFAULT: { minConfidence: 0.88, minConfidenceGap: 0.12, minInputLength: 4, maxDist: 1 },
  CHAT: { minConfidence: 0.88 / 1.12, minConfidenceGap: 0.12 / 1.12, minInputLength: Math.round(4 / 1.12), maxDist: 1 },
  EMAIL: { minConfidence: 0.99, minConfidenceGap: 0.12 / 0.88, minInputLength: Math.round(4 / 0.88), maxDist: 1 },
};

function shouldAutoCommit(cfg, input, word, dist, conf, runnerConf, hasExact) {
  if (hasExact) return false;
  if (input.length < cfg.minInputLength) return false;
  if (word === input) return false;
  if (dist < 1 || dist > cfg.maxDist) return false;
  if (conf < cfg.minConfidence) return false;
  const gap = runnerConf == null ? 1.0 : conf - runnerConf;
  return gap >= cfg.minConfidenceGap;
}

// Mirrors LatinLanguageProvider.suggest() for a non-blank current word, ignoring user dictionary and
// personal n-grams (fresh install). Returns up to 8 candidates plus the auto-commit decision.
function suggestBaseline(langs, rawInput, profile = 'DEFAULT', contextTokens = []) {
  const input = norm(rawInput);
  const weights = languageWeights(langs, contextTokens, input);
  const hasExact = langs.some((l) => l.model.words.has(input));
  const agg = new Map();
  for (const l of langs) {
    const m = l.model;
    const per = new Map();
    if (m.words.has(input)) per.set(input, { word: input, distance: 0, frequency: m.words.get(input), isPrefixMatch: true });
    for (const c of lookupPrefix(m, input, 16)) {
      if (!per.has(c.word)) per.set(c.word, c);
      if (per.size >= 16) break;
    }
    if (input.length >= 4) {
      for (const c of lookupCorrections(m, input, 16)) {
        if (!per.has(c.word)) per.set(c.word, c);
        if (per.size >= 16) break;
      }
    }
    const w = weights[l.lang] || 0;
    for (const c of per.values()) {
      const wr = rankScore(m, input, c) * clamp(0.6 + w, 0.6, 1.6);
      const wc = clamp(0.88 * confidence(m, input, c) + 0.12 * w, 0.05, 1);
      const cur = agg.get(c.word);
      if (!cur || wr > cur.rs || (wr === cur.rs && wc > cur.conf)) agg.set(c.word, { c, rs: wr, conf: wc });
    }
  }
  const sorted = [...agg.values()]
    .sort((a, b) => b.rs - a.rs || b.c.frequency - a.c.frequency || cmpStr(a.c.word, b.c.word))
    .slice(0, 8);
  const top = sorted[0];
  const auto =
    top && shouldAutoCommit(PROFILES[profile], input, top.c.word, top.c.distance, top.conf, sorted[1]?.conf, hasExact)
      ? top.c.word
      : null;
  return { candidates: sorted.map((s) => s.c.word), auto, topConf: top?.conf ?? 0, hasExact };
}

// ---------- Reference noisy-channel scorer (headroom estimate only) ----------
const ROWS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'];
const OFFS = [0, 0.25, 0.75];
const POS = {};
ROWS.forEach((r, y) => [...r].forEach((ch, x) => (POS[ch] = [x + OFFS[y], y])));
function keyDist(a, b) {
  const pa = POS[a], pb = POS[b];
  if (!pa || !pb) return 9;
  return Math.hypot(pa[0] - pb[0], pa[1] - pb[1]);
}
const isAdj = (a, b) => a !== b && keyDist(a, b) <= 1.3;

// Weighted Damerau-Levenshtein: cost of typing `typed` when `intended` was meant.
function channelCost(typed, intended) {
  const n = typed.length, m = intended.length;
  const D = Array.from({ length: n + 1 }, () => new Array(m + 1).fill(Infinity));
  D[0][0] = 0;
  for (let i = 0; i <= n; i++) {
    for (let j = 0; j <= m; j++) {
      const cur = D[i][j];
      if (cur === Infinity) continue;
      if (i < n && j < m) {
        const a = typed[i], b = intended[j];
        const sub = a === b ? 0 : isAdj(a, b) ? 4 : 8;
        D[i + 1][j + 1] = Math.min(D[i + 1][j + 1], cur + sub);
      }
      if (j < m) D[i][j + 1] = Math.min(D[i][j + 1], cur + 5); // omission
      if (i < n) {
        const a = typed[i];
        const prev = i > 0 ? typed[i - 1] : null;
        const next = i + 1 < n ? typed[i + 1] : null;
        const cheap = a === prev || (prev && isAdj(a, prev)) || (next && isAdj(a, next));
        D[i + 1][j] = Math.min(D[i + 1][j], cur + (cheap ? 5 : 7.5)); // insertion
      }
      if (i + 1 < n && j + 1 < m && typed[i] === intended[j + 1] && typed[i + 1] === intended[j] && typed[i] !== typed[i + 1]) {
        D[i + 2][j + 2] = Math.min(D[i + 2][j + 2], cur + 5); // transposition
      }
    }
  }
  return D[n][m];
}

function buildNcIndex(words, maxDist) {
  // Symmetric-delete index up to maxDist over every word of at most 20 letters. Longer words are left out to bound
  // the index size; the baseline numbers recorded on 2026-09-24 were measured with this limit.
  const del = new Map();
  const add = (k, w) => {
    let l = del.get(k);
    if (!l) del.set(k, (l = new Set()));
    l.add(w);
  };
  for (const w of words.keys()) {
    if (w.length > 20) continue;
    add(w, w);
    let frontier = new Set([w]);
    for (let d = 1; d <= maxDist; d++) {
      const next = new Set();
      for (const f of frontier) for (const x of deletes1(f)) if (x.length >= 1) next.add(x);
      for (const x of next) add(x, w);
      frontier = next;
    }
  }
  return del;
}

function ncCandidates(lang, input, maxDist) {
  const out = new Set();
  let frontier = new Set([input]);
  const probe = (k) => {
    const l = lang.ncIndex.get(k);
    if (l) for (const w of l) out.add(w);
  };
  probe(input);
  for (let d = 1; d <= maxDist; d++) {
    const next = new Set();
    for (const f of frontier) for (const x of deletes1(f)) next.add(x);
    for (const x of next) probe(x);
    frontier = next;
  }
  return out;
}

// Returns ranked candidates with posteriors. Literal input competes as "keep what I typed".
function suggestNoisyChannel(langs, rawInput, { maxDist = 2, minLen = 3, threshold = 0.85, oovLogPrior = Math.log(300) } = {}) {
  const input = norm(rawInput);
  const hasExact = langs.some((l) => l.model.words.has(input));
  const scores = new Map();
  const w = languageWeights(langs, [], input);
  for (const l of langs) {
    const md = input.length >= 7 ? maxDist : Math.min(1, maxDist);
    for (const cand of ncCandidates(l, input, md)) {
      const f = l.model.words.get(cand);
      if (!f) continue;
      const cost = channelCost(input, cand);
      const s = Math.log(f) + Math.log(Math.max(w[l.lang], 0.05)) - cost;
      if (!scores.has(cand) || s > scores.get(cand)) scores.set(cand, s);
    }
  }
  if (!hasExact) scores.set('\u0000literal', oovLogPrior);
  const arr = [...scores.entries()].sort((a, b) => b[1] - a[1]);
  const mx = arr[0][1];
  const z = arr.reduce((acc, [, s]) => acc + Math.exp(s - mx), 0);
  const post = arr.map(([word, s]) => ({ word, p: Math.exp(s - mx) / z }));
  const top = post[0];
  const auto =
    !hasExact && input.length >= minLen && top.word !== '\u0000literal' && top.word !== input && top.p >= threshold
      ? top.word
      : null;
  return { candidates: post.filter((x) => x.word !== '\u0000literal').map((x) => x.word).slice(0, 8), auto, hasExact };
}

module.exports = { loadFreq, buildModel, buildNcIndex, suggestBaseline, suggestNoisyChannel, PROFILES, POS, isAdj, norm };
