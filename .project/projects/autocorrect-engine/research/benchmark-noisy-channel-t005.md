# Noisy-channel scorer, default settings

- Tap-noise real-word rates (excluded from sets): EN usage 18.4%, EN uniform 5.7%, NL usage 17.4%, NL uniform 4.5%

| Set | n | Right | Wrong | Precision | Top-1 | Top-3 | Typed is dict word | p50 us | p95 us |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tap EN usage, EN | 1500 | 70.8 | 0.7 | 99.1 | 87.8 | 92.4 | 0.0 | 58 | 178 |
| tap EN uniform, EN | 1500 | 44.1 | 1.1 | 97.6 | 80.9 | 88.1 | 0.0 | 45 | 105 |
| tap NL usage, NL | 1500 | 69.2 | 0.4 | 99.4 | 86.7 | 90.7 | 0.0 | 84 | 310 |
| tap NL uniform, NL | 1500 | 47.5 | 0.9 | 98.2 | 82.6 | 87.3 | 0.0 | 28 | 64 |
| tap NL usage, NL+EN | 1500 | 61.8 | 0.7 | 98.8 | 85.3 | 89.9 | 3.7 | 42 | 144 |
| tap EN usage, NL+EN | 1500 | 59.9 | 0.7 | 98.8 | 85.1 | 91.9 | 1.0 | 47 | 156 |
| tap NL uniform, NL+EN | 1500 | 38.5 | 0.6 | 98.5 | 81.3 | 86.9 | 0.9 | 42 | 100 |
| harness EN usage, EN (optimistic) | 1113 | 69.6 | 0.8 | 98.9 | 92.6 | 99.5 | 0.0 | 37 | 97 |
| real EN curated, EN | 100 | 81.0 | 0.0 | 100.0 | 95.0 | 95.0 | 2.0 | 34 | 83 |
| real EN Wikipedia, EN | 3941 | 28.9 | 0.7 | 97.7 | 61.5 | 63.2 | 0.3 | 24 | 52 |
| EN missing apostrophes, EN | 14 | 0.0 | 0.0 | 100.0 | 0.0 | 0.0 | 100.0 | 32 | 95 |
| real NL curated, NL | 60 | 88.3 | 0.0 | 100.0 | 100.0 | 100.0 | 1.7 | 39 | 57 |
| real NL extra, NL | 141 | 86.5 | 0.0 | 100.0 | 94.3 | 97.9 | 0.0 | 34 | 68 |
| real NL all, NL+EN | 201 | 85.6 | 0.0 | 100.0 | 95.5 | 98.5 | 0.5 | 43 | 80 |

| Clean text | Words | False corrections | Per 1,000 | Examples |
| --- | --- | --- | --- | --- |
| clean EN, EN | 7999 | 0 | 0.00 |  |
| clean NL, NL | 7993 | 1 | 0.13 | raport->rapport |
| clean EN, NL+EN | 7999 | 1 | 0.13 | Rima->Prima |
| clean NL, NL+EN | 7993 | 0 | 0.00 |  |

| Out-of-dictionary set | n | Already in dictionary | Changed | Examples |
| --- | --- | --- | --- | --- |
| oov.txt, NL+EN | 313 | 87 | 2 | async->sync, mergen->morgen |
| oov.txt, EN | 313 | 48 | 2 | Thijs->This, async->sync |

## Examples
- tap EN usage, EN, wrong: neeed->need (meant needed); lnog->long (meant along); ini->in (meant mini); nlow->now (meant blow); tko->to (meant too); hde->he (meant she); aou->you (meant about); wya->way (meant away)
- tap EN usage, EN, missed: deturm [] (meant return); eud [end, feud, ed] (meant did); cuck [fuck, cuckoo, chuck] (meant fuck); hdwish [] (meant jewish); ahs [has, as, ah] (meant has); guyss [guys, guess] (meant guys); islans [island, islands] (meant island); uor [our, or, for] (meant our)
- tap EN uniform, EN, wrong: yees->yes (meant eyes); beautifull->beautiful (meant beautifully); fuucker->fucker (meant fucked); cafr->car (meant cafe); wsighs->sighs (meant weighs); dlo->do (meant doo); spmetime->sometime (meant sometimes); lke->like (meant luke)
- tap EN uniform, EN, missed: paramedivs [paramedics] (meant paramedics); tarely [rarely, barely] (meant rarely); sobdr [sober] (meant sober); ppsts [posts] (meant posts); minthly [monthly] (meant monthly); hoel [hotel, hole, joel] (meant hole); pilors [pilots] (meant pilots); psychologsit [psychologist] (meant psychologist)
- tap NL usage, NL, wrong: niiet->niet (meant niets); trn->ten (meant toen); stopp->stop (meant stoppen); reven->even (meant tegen); dta->dat (meant sta); blijen->blijven (meant blijken)
- tap NL usage, NL, missed: waa [was, waar, waarom] (meant waar); feitwlijk [feitelijk] (meant feitelijk); saaii [saai, saaie] (meant saai); hoore [hoor, hoorde, hoort] (meant hoorde); zuden [zouden, zuiden, zaden] (meant zouden); lerk [leek, kerk, lek] (meant leek); faan [gaan, aan, fan] (meant gaan); vinf [vind, vijf, ving] (meant vijf)
- tap NL uniform, NL, wrong: pratten->praten (meant praatten); jaer->jaar (meant jager); pzer->per (meant gozer); veroorzaamt->veroorzaakt (meant veroorzaakte); vean->van (meant evan); vehoord->gehoord (meant verhoord); lng->lang (meant ling); mayr->maar (meant mary)
- tap NL uniform, NL, missed: tatent [attent, talent, patent] (meant attent); opvieden [opvoeden] (meant opvoeden); kaddy [maddy, kady, addy] (meant maddy); deuppel [druppel] (meant druppel); sttructuir [] (meant structuur); resents [presents] (meant presents); mademoisellr [mademoiselle] (meant mademoiselle); bwoordeeld [beoordeeld] (meant beoordeeld)
- tap NL usage, NL+EN, wrong: niiet->niet (meant niets); abnd->and (meant band); dlo->do (meant dol); stopp->stop (meant stoppen); bda->bad (meant had); uhj->uh (meant hij); reven->even (meant tegen); daay->day (meant dat)
- tap NL usage, NL+EN, missed: waa [was, waar, waarom] (meant waar); nite [niet, nite, note] (meant niet); feitwlijk [feitelijk] (meant feitelijk); saaii [saai, saaie] (meant saai); hoore [hoor, hoorde, hoort] (meant hoorde); zuden [zouden, zuiden, zaden] (meant zouden); lerk [leek, kerk, lek] (meant leek); faan [gaan, aan, fan] (meant gaan)
- tap EN usage, NL+EN, wrong: deno->denk (meant demo); neeed->need (meant needed); rpook->rook (meant took); ini->in (meant mini); nlow->now (meant blow); tko->to (meant too); pje->je (meant pie); aou->you (meant about)
- tap EN usage, NL+EN, missed: adn [and, dan, an] (meant and); esrves [serves] (meant serves); deturm [] (meant return); eud [oud, eed, ed] (meant did); wih [with, wij, wish] (meant with); hten [then, haten, hen] (meant then); cuck [fuck, cuckoo, chuck] (meant fuck); hdwish [] (meant jewish)
- tap NL uniform, NL+EN, wrong: makke->make (meant makkie); pratten->praten (meant praatten); jaer->jaar (meant jager); veroorzaamt->veroorzaakt (meant veroorzaakte); vehoord->gehoord (meant verhoord); mayr->maar (meant mary); gveolgen->gevolgen (meant gevlogen); drinen->drinken (meant dringen)
- tap NL uniform, NL+EN, missed: tatent [attent, talent, patent] (meant attent); opvieden [opvoeden] (meant opvoeden); ateelt [steelt, teelt] (meant steelt); allemschtig [allemachtig] (meant allemachtig); specialieit [specialiteit] (meant specialiteit); kaddy [maddy, kady, daddy] (meant maddy); deuppel [druppel] (meant druppel); sttructuir [] (meant structuur)
- harness EN usage, EN (optimistic), wrong: glood->good (meant blood); desr->dear (meant deer); loh->oh (meant ooh); whre->where (meant whore); lotf->lot (meant loft); mce->me (meant mice); waht->what (meant want); whil->while (meant whip)
- harness EN usage, EN (optimistic), missed: vefa [vera, vega] (meant vega); ights [lights, nights, rights] (meant lights); przyers [prayers] (meant prayers); dlin [doin, lin, din] (meant doin); jome [home, joke, come] (meant joke); arres [arrest, arrested, arrests] (meant arrest); fw [few, fa, fe] (meant few); hshh [shhh, shh] (meant shhh)
- real EN curated, EN, missed: tommorow [] (meant tomorrow); wierd [weird, wired, wield] (meant weird); calender [calendar] (meant calendar); foward [forward, coward, toward] (meant forward); happend [happened, happens, happen] (meant happened); peice [piece, peace, price] (meant piece); truely [truly] (meant truly); helo [hello, help, hell] (meant hello)
- real EN Wikipedia, EN, wrong: adviced->advice (meant advised); atain->again (meant attain); casion->casino (meant caisson); cxan->can (meant cyan); dyas->days (meant dryas); efel->feel (meant evil); eles->else (meant eels); exerciese->exercise (meant exercises)
- real EN Wikipedia, EN, missed: aberation [] (meant aberration); abilityes [abilities] (meant abilities); abscence [absence] (meant absence); abondoning [abandoning] (meant abandoning); abondons [] (meant abandons); aborigene [] (meant aborigine); accesories [accessories] (meant accessories); abortificant [] (meant abortifacient)
- EN missing apostrophes, EN, missed: dont [dont, don, font] (meant don't); im [in, him, i] (meant i'm); youre [your, youre, yours] (meant you're); thats [thats, that, hats] (meant that's); didnt [didnt, didn, dint] (meant didn't); doesnt [doesnt, doesn] (meant doesn't); isnt [isnt, isn, ist] (meant isn't); ive [give, live, five] (meant i've)
- real NL curated, NL, missed: trouwes [trouwens, trouwe, trouwen] (meant trouwens); oko [ook, oké, oke] (meant ook); heben [hebben, heben, heen] (meant hebben); groejtes [groetjes] (meant groetjes); groetn [groeten, groten, groen] (meant groeten); eventeel [eventueel, evenveel] (meant eventueel); eevn [even, een] (meant even)
- real NL extra, NL, missed: wekren [werken, weken, weren] (meant werken); berigt [] (meant bericht); berichtej [berichten, berichtje] (meant berichtje); groetjse [groetjes] (meant groetjes); eetn [een, eten, eet] (meant eten); koffe [koffie, koffer, koffers] (meant koffie); betre [beter, betreft, betrek] (meant beter); mooii [mooi, mooie, moois] (meant mooi)
- real NL all, NL+EN, missed: trouwes [trouwens, trouwe, trouwen] (meant trouwens); oko [ook, oké, ok] (meant ook); hbe [heb, he, be] (meant heb); heben [hebben, heben, heen] (meant hebben); groejtes [groetjes] (meant groetjes); groetn [groeten, groten, groen] (meant groeten); eventeel [eventueel, evenveel] (meant eventueel); eevn [even, een] (meant even)
