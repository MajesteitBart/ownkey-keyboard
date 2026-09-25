const path = require('path');
const E = require('./engine');

const DICT = process.argv[2];
const load = (lang) => {
  const words = E.loadFreq(path.join(DICT, `${lang}_50k.txt`));
  return { lang, words, model: E.buildModel(words) };
};
console.time('load');
const en = load('en');
const nl = load('nl');
console.timeEnd('load');
console.time('ncIndex');
for (const l of [en, nl]) l.ncIndex = E.buildNcIndex(l.words, 2);
console.timeEnd('ncIndex');

const configs = {
  'EN only': [{ ...en, isPrimary: true }],
  'NL only': [{ ...nl, isPrimary: true }],
  'NL+EN': [{ ...nl, isPrimary: true }, { ...en, isPrimary: false }],
};

// Deterministic PRNG
let seed = 42;
const rnd = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296);
const letters = 'abcdefghijklmnopqrstuvwxyz';
const neighbors = {};
for (const a of letters) neighbors[a] = [...letters].filter((b) => E.isAdj(a, b));

function makeTypo(w) {
  const r = rnd();
  const i = Math.floor(rnd() * w.length);
  if (r < 0.55) {
    const nb = neighbors[w[i]];
    if (!nb || !nb.length) return null;
    return w.slice(0, i) + nb[Math.floor(rnd() * nb.length)] + w.slice(i + 1);
  }
  if (r < 0.75) return w.length > 2 ? w.slice(0, i) + w.slice(i + 1) : null;
  if (r < 0.9) {
    const nb = neighbors[w[i]] || [];
    const ch = rnd() < 0.4 ? w[i] : nb[Math.floor(rnd() * nb.length)] || w[i];
    return w.slice(0, i) + ch + w.slice(i);
  }
  if (w.length < 2 || i === w.length - 1 || w[i] === w[i + 1]) return null;
  return w.slice(0, i) + w[i + 1] + w[i] + w.slice(i + 2);
}

function sampleWords(lang, n, { minLen = 3, maxRank = 10000, tokenWeighted }) {
  const ranked = [...lang.words.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, maxRank)
    .filter(([w]) => /^[a-z]+$/.test(w) && w.length >= minLen);
  const out = [];
  if (tokenWeighted) {
    const total = ranked.reduce((a, [, f]) => a + f, 0);
    while (out.length < n) {
      let x = rnd() * total;
      for (const [w, f] of ranked) {
        if ((x -= f) <= 0) {
          out.push(w);
          break;
        }
      }
    }
  } else {
    while (out.length < n) out.push(ranked[100 + Math.floor(rnd() * (ranked.length - 100))][0]);
  }
  return out;
}

function buildTypos(lang, words) {
  const pairs = [];
  let realWord = 0, total = 0;
  for (const w of words) {
    let t = null;
    for (let k = 0; k < 5 && (t == null || t === w); k++) t = makeTypo(w);
    if (t == null || t === w) continue;
    total++;
    if (lang.words.has(t)) {
      realWord++;
      continue;
    }
    pairs.push([t, w]);
  }
  return { pairs, realWordRate: realWord / total, total };
}

const pct = (a, b) => (b ? ((100 * a) / b).toFixed(1) + '%' : 'n/a');

function evaluate(name, langs, pairs) {
  const rows = [];
  const engines = {
    'baseline DEFAULT': (t) => E.suggestBaseline(langs, t, 'DEFAULT'),
    'baseline CHAT': (t) => E.suggestBaseline(langs, t, 'CHAT'),
    'noisy-channel ref': (t) => E.suggestNoisyChannel(langs, t),
  };
  for (const [ename, fn] of Object.entries(engines)) {
    let acOk = 0, acWrong = 0, top1 = 0, top3 = 0, exact = 0;
    const wrongEx = [], missEx = [];
    for (const [typo, want] of pairs) {
      const r = fn(typo);
      if (r.hasExact) exact++;
      if (r.auto === want) acOk++;
      else if (r.auto) {
        acWrong++;
        if (wrongEx.length < 6) wrongEx.push(`${typo}->${r.auto} (wanted ${want})`);
      } else if (missEx.length < 6) missEx.push(`${typo} [${r.candidates.slice(0, 3).join(', ')}]`);
      if (r.candidates[0] === want) top1++;
      if (r.candidates.slice(0, 3).includes(want)) top3++;
    }
    const n = pairs.length;
    rows.push({ engine: ename, n, 'autocorrect right': pct(acOk, n), 'autocorrect wrong': pct(acWrong, n), 'top-1': pct(top1, n), 'top-3': pct(top3, n), 'typo is dict word': pct(exact, n), wrongEx, missEx });
  }
  console.log(`\n## ${name}`);
  console.table(rows.map(({ wrongEx, missEx, ...r }) => r));
  for (const r of rows) {
    if (r.wrongEx.length) console.log(`  ${r.engine} wrong: ${r.wrongEx.join('; ')}`);
    if (r.missEx.length && r.engine !== 'noisy-channel ref') console.log(`  ${r.engine} missed: ${r.missEx.join('; ')}`);
  }
}

function falsePositives(name, langs, words) {
  const rows = [];
  for (const [ename, fn] of Object.entries({
    'baseline DEFAULT': (t) => E.suggestBaseline(langs, t, 'DEFAULT'),
    'baseline CHAT': (t) => E.suggestBaseline(langs, t, 'CHAT'),
    'noisy-channel ref': (t) => E.suggestNoisyChannel(langs, t),
  })) {
    const changed = [];
    let inDict = 0;
    for (const w of words) {
      const r = fn(w);
      if (r.hasExact) inDict++;
      if (r.auto && r.auto !== E.norm(w)) changed.push(`${w}->${r.auto}`);
    }
    rows.push({ engine: ename, n: words.length, 'already in dict': inDict, 'wrongly changed': changed.length, examples: changed.slice(0, 10).join(', ') });
  }
  console.log(`\n## ${name}`);
  console.table(rows);
}

// ---------- Datasets ----------
const EN_REAL = `teh:the taht:that adn:and hte:the waht:what jsut:just konw:know wiht:with whcih:which woudl:would coudl:could shoudl:should abotu:about becasue:because becuase:because recieve:receive definately:definitely seperate:separate occured:occurred untill:until tommorow:tomorrow tomorow:tomorrow wierd:weird thier:their freind:friend beleive:believe goverment:government accomodate:accommodate begining:beginning calender:calendar existance:existence finaly:finally foward:forward happend:happened independant:independent knowlege:knowledge neccessary:necessary peice:piece prefered:preferred realy:really sucess:success suprise:surprise truely:truly thnaks:thanks thnak:thank pleae:please plase:please helo:hello meetign:meeting agian:again somethign:something anythign:anything probaly:probably actualy:actually basicaly:basically yuo:you yoi:you tje:the rhe:the thw:the amd:and abd:and wirh:with woth:with hpw:how whst:what sre:are arw:are cna:can nwo:now knwo:know peopel:people poeple:people beacuse:because tonihgt:tonight yesturday:yesterday wendesday:wednesday febuary:february definetly:definitely differnt:different diffrent:different enviroment:environment experiance:experience grammer:grammar immediatly:immediately intresting:interesting libary:library noticable:noticeable occassion:occasion recomend:recommend relevent:relevant rember:remember remeber:remember resturant:restaurant sentance:sentence strenght:strength succesful:successful wich:which writting:writing tounge:tongue`;
const EN_APOS = `dont:don't im:i'm youre:you're thats:that's didnt:didn't doesnt:doesn't isnt:isn't ive:i've wasnt:wasn't couldnt:couldn't wouldnt:wouldn't shouldnt:shouldn't havent:haven't arent:aren't`;
const NL_REAL = `mischien:misschien misschein:misschien eigelijk:eigenlijk eigenlik:eigenlijk natuurlik:natuurlijk natuurljik:natuurlijk gewon:gewoon mrogen:morgen bedakt:bedankt bednakt:bedankt alsjeblief:alsjeblieft waarschijnlik:waarschijnlijk ontvagen:ontvangen vergaderign:vergadering trouwes:trouwens waneer:wanneer binnekort:binnenkort zeekr:zeker gistern:gisteren helemal:helemaal belangrjk:belangrijk volgnede:volgende dnakjewel:dankjewel afsraak:afspraak afpsraak:afspraak vanmidag:vanmiddag vadnaag:vandaag morgne:morgen neit:niet oko:ook wta:wat ehb:heb hbe:heb heben:hebben kunen:kunnen moetne:moeten zjin:zijn wodt:wordt gedan:gedaan wekrt:werkt vriendelik:vriendelijk groejtes:groetjes groetn:groeten verschilende:verschillende informtie:informatie eventeel:eventueel ongeveeer:ongeveer iedereeen:iedereen tegenwoordg:tegenwoordig volgnes:volgens waarmo:waarom omadt:omdat ondat:omdat zoadt:zodat alvats:alvast graaag:graag grag:graag eevn:even latne:laten wetne:weten`;
const parse = (s) => s.split(/\s+/).filter(Boolean).map((p) => p.split(':'));

const OOV = `Bart Meeren Ownkey Voxtral Todoist Tailscale Supabase Anthropic Gboard SwiftKey Delano kubernetes webhook repo config async standup onboarding dashboard deployen gedeployed releasen mergen pushen committen appen appje mailtje linkje berichtje ff idd wss gwn zsm thnx omg brb gonna wanna gotta yup nope haha hahaha lol Utrecht Amersfoort Zwolle Hilversum Dordrecht Wageningen Bitwarden Hermes Obsidian Claude Fable`.split(/\s+/);

// ---------- Run ----------
const sEN = buildTypos(en, sampleWords(en, 1500, { tokenWeighted: true }));
const uEN = buildTypos(en, sampleWords(en, 1500, { tokenWeighted: false }));
const sNL = buildTypos(nl, sampleWords(nl, 1500, { tokenWeighted: true }));
const uNL = buildTypos(nl, sampleWords(nl, 1500, { tokenWeighted: false }));
console.log('\nReal-word error rate (single touch typo lands on another dictionary word, invisible without context):');
console.log(`  EN usage-weighted ${pct(sEN.realWordRate * sEN.total, sEN.total)}, EN vocabulary ${pct(uEN.realWordRate * uEN.total, uEN.total)}, NL usage-weighted ${pct(sNL.realWordRate * sNL.total, sNL.total)}, NL vocabulary ${pct(uNL.realWordRate * uNL.total, uNL.total)}`);

evaluate('EN synthetic touch typos, usage-weighted (EN only)', configs['EN only'], sEN.pairs);
evaluate('EN synthetic touch typos, vocabulary-uniform ranks 100-10k (EN only)', configs['EN only'], uEN.pairs);
evaluate('NL synthetic touch typos, usage-weighted (NL only)', configs['NL only'], sNL.pairs);
evaluate('NL synthetic touch typos, usage-weighted (NL+EN)', configs['NL+EN'], sNL.pairs);
evaluate('EN synthetic touch typos, usage-weighted (NL+EN)', configs['NL+EN'], sEN.pairs);
evaluate('EN real-world misspellings (EN only)', configs['EN only'], parse(EN_REAL));
evaluate('EN missing apostrophes (EN only)', configs['EN only'], parse(EN_APOS));
evaluate('NL real-world misspellings (NL only)', configs['NL only'], parse(NL_REAL));
evaluate('NL real-world misspellings (NL+EN)', configs['NL+EN'], parse(NL_REAL));
falsePositives('Legit out-of-dictionary words: names, jargon, NL chat shorthand (NL+EN)', configs['NL+EN'], OOV);

// Completion reach: can a 5-letter prefix surface a long word at all?
let reach = 0, oracle = 0, n = 0;
for (const w of sampleWords(en, 800, { tokenWeighted: false, minLen: 8 })) {
  const p = w.slice(0, 5);
  n++;
  if (E.suggestBaseline(configs['EN only'], p).candidates.slice(0, 3).includes(w)) reach++;
  const all = [...en.words.entries()].filter(([x]) => x.startsWith(p) && x !== p).sort((a, b) => b[1] - a[1]).slice(0, 3).map(([x]) => x);
  if (all.includes(w)) oracle++;
}
console.log(`\n## Completion from 5-letter prefix, EN words of 8+ letters (n=${n})`);
console.log(`  current top-3 hit ${pct(reach, n)} vs frequency-oracle top-3 ${pct(oracle, n)}`);

// Dictionary coverage of intended forms
console.log(`\n## Apostrophe forms present in EN dictionary: ${parse(EN_APOS).filter(([, w]) => en.words.has(w)).length}/${parse(EN_APOS).length}; misspelled forms present: ${parse(EN_APOS).filter(([t]) => en.words.has(t)).length}/${parse(EN_APOS).length}`);
