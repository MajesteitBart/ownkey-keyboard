const path = require('path');
const E = require('./engine');
const DICT = process.argv[2];
const en = { lang: 'en', words: E.loadFreq(path.join(DICT, 'en_50k.txt')) }; en.model = E.buildModel(en.words); en.isPrimary = true;
const nl = { lang: 'nl', words: E.loadFreq(path.join(DICT, 'nl_50k.txt')) }; nl.model = E.buildModel(nl.words); nl.isPrimary = true;
// Most aggressive user settings: min confidence 50%, gap 0%, min length 3, chat 130%.
E.PROFILES.MAX = { minConfidence: Math.max(0.5 / 1.3, 0.5), minConfidenceGap: 0, minInputLength: Math.max(2, Math.round(3 / 1.3)), maxDist: 1 };
let seed = 7; const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296);
for (const L of [en, nl]) {
  const ranked = [...L.words.entries()].sort((a, b) => b[1] - a[1]).slice(0, 10000).filter(([w]) => /^[a-z]+$/.test(w) && w.length >= 3);
  const total = ranked.reduce((a, [, f]) => a + f, 0);
  let n = 0, ok = 0, wrong = 0; const ex = [];
  while (n < 1500) {
    let x = rnd() * total, w; for (const [ww, f] of ranked) { if ((x -= f) <= 0) { w = ww; break; } }
    const i = Math.floor(rnd() * w.length); const nb = 'abcdefghijklmnopqrstuvwxyz'.split('').filter(b => E.isAdj(w[i], b)); if (!nb.length) continue;
    const t = w.slice(0, i) + nb[Math.floor(rnd() * nb.length)] + w.slice(i + 1); if (L.words.has(t)) continue;
    n++; const r = E.suggestBaseline([L], t, 'MAX');
    if (r.auto === w) ok++; else if (r.auto) { wrong++; if (ex.length < 12) ex.push(`${t}->${r.auto} (wanted ${w})`); }
  }
  console.log(`${L.lang} MAX sliders: autocorrect right ${(100*ok/n).toFixed(1)}%, wrong ${(100*wrong/n).toFixed(1)}% (n=${n}; adjacent-key substitutions only)`);
  console.log('  wrong examples:', ex.join('; '));
}
