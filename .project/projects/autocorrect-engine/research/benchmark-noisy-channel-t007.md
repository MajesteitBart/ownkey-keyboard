# Noisy-channel scorer, default settings

- Tap-noise real-word rates (excluded from sets): EN usage 18.4%, EN uniform 5.7%, NL usage 17.4%, NL uniform 4.5%

| Set | n | Right | Wrong | Precision | Top-1 | Top-3 | Typed is dict word | p50 us | p95 us |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tap EN usage, EN | 1500 | 70.5 | 0.7 | 99.0 | 86.9 | 91.5 | 0.0 | 58 | 186 |
| tap EN uniform, EN | 1500 | 44.5 | 1.1 | 97.5 | 80.3 | 87.5 | 0.0 | 58 | 152 |
| tap NL usage, NL | 1500 | 69.3 | 0.3 | 99.5 | 86.6 | 90.6 | 0.0 | 77 | 269 |
| tap NL uniform, NL | 1500 | 48.3 | 1.1 | 97.8 | 82.4 | 86.9 | 0.0 | 29 | 82 |
| tap NL usage, NL+EN | 1500 | 62.3 | 0.8 | 98.7 | 85.3 | 90.2 | 1.1 | 52 | 204 |
| tap EN usage, NL+EN | 1500 | 59.7 | 0.8 | 98.7 | 84.4 | 91.3 | 0.7 | 41 | 174 |
| tap NL uniform, NL+EN | 1500 | 38.6 | 0.7 | 98.3 | 81.4 | 86.9 | 0.6 | 46 | 138 |
| harness EN usage, EN (optimistic) | 1113 | 69.1 | 0.9 | 98.7 | 91.6 | 98.4 | 0.0 | 37 | 110 |
| real EN curated, EN | 100 | 82.0 | 0.0 | 100.0 | 95.0 | 95.0 | 0.0 | 42 | 95 |
| real EN Wikipedia, EN | 3941 | 29.3 | 0.7 | 97.6 | 62.4 | 64.1 | 0.1 | 28 | 65 |
| EN missing apostrophes, EN | 14 | 85.7 | 0.0 | 100.0 | 100.0 | 100.0 | 0.0 | 159 | 364 |
| real NL curated, NL | 60 | 90.0 | 0.0 | 100.0 | 100.0 | 100.0 | 0.0 | 107 | 253 |
| real NL extra, NL | 141 | 86.5 | 0.0 | 100.0 | 94.3 | 97.9 | 0.0 | 37 | 93 |
| real NL all, NL+EN | 201 | 86.1 | 0.0 | 100.0 | 95.5 | 98.5 | 0.0 | 55 | 141 |

| Clean text | Words | False corrections | Per 1,000 | Examples |
| --- | --- | --- | --- | --- |
| clean EN, EN | 7999 | 0 | 0.00 |  |
| clean NL, NL | 7993 | 1 | 0.13 | raport->rapport |
| clean EN, NL+EN | 7999 | 1 | 0.13 | Rima->Prima |
| clean NL, NL+EN | 7993 | 0 | 0.00 |  |

| Out-of-dictionary set | n | Already in dictionary | Changed | Examples |
| --- | --- | --- | --- | --- |
| oov.txt, NL+EN | 313 | 79 | 2 | async->sync, mergen->morgen |
| oov.txt, EN | 313 | 29 | 2 | Thijs->This, async->sync |

## Examples
- tap EN usage, EN, wrong: doesm->does (meant doesn); neeed->need (meant needed); lnog->long (meant along); ini->in (meant mini); nlow->now (meant blow); tko->to (meant too); hde->he (meant she); aou->you (meant about)
- tap EN usage, EN, missed: deturm [] (meant return); eud [end, feud, ed] (meant did); cuck [fuck, cuckoo, chuck] (meant fuck); hdwish [] (meant jewish); ahs [has, as, ah] (meant has); guyss [guys, guess] (meant guys); islans [island, islands] (meant island); uor [our, or, for] (meant our)
- tap EN uniform, EN, wrong: yees->yes (meant eyes); beautifull->beautiful (meant beautifully); fuucker->fucker (meant fucked); dond->done (meant fond); cafr->car (meant cafe); wsighs->sighs (meant weighs); dlo->do (meant doo); spmetime->sometime (meant sometimes)
- tap EN uniform, EN, missed: paramedivs [paramedics] (meant paramedics); tarely [rarely, barely] (meant rarely); sobdr [sober] (meant sober); ppsts [posts] (meant posts); minthly [monthly] (meant monthly); hoel [hotel, hole, joel] (meant hole); pilors [pilots] (meant pilots); psychologsit [psychologist] (meant psychologist)
- tap NL usage, NL, wrong: niiet->niet (meant niets); stopp->stop (meant stoppen); reven->even (meant tegen); dta->dat (meant sta); blijen->blijven (meant blijken)
- tap NL usage, NL, missed: waa [was, waar, waarom] (meant waar); feitwlijk [feitelijk] (meant feitelijk); saaii [saai, saaie] (meant saai); hoore [hoor, hoorde, hoort] (meant hoorde); zuden [zouden, zuiden, zaden] (meant zouden); lerk [leek, kerk, lek] (meant leek); faan [gaan, aan, fan] (meant gaan); vinf [vind, vijf, ving] (meant vijf)
- tap NL uniform, NL, wrong: hoodd->hoofd (meant hood); pratten->praten (meant praatten); jaer->jaar (meant jager); pzer->per (meant gozer); veroorzaamt->veroorzaakt (meant veroorzaakte); hoogg->hoog (meant hogg); vean->van (meant evan); vehoord->gehoord (meant verhoord)
- tap NL uniform, NL, missed: tatent [attent, talent, patent] (meant attent); opvieden [opvoeden] (meant opvoeden); kaddy [maddy, paddy, daddy] (meant maddy); deuppel [druppel] (meant druppel); sttructuir [] (meant structuur); resents [presents] (meant presents); mademoisellr [mademoiselle] (meant mademoiselle); bwoordeeld [beoordeeld] (meant beoordeeld)
- tap NL usage, NL+EN, wrong: niiet->niet (meant niets); abnd->and (meant band); dlo->do (meant dol); aint->ain't (meant saint); stopp->stop (meant stoppen); bda->bad (meant had); uhj->uh (meant hij); reven->even (meant tegen)
- tap NL usage, NL+EN, missed: waa [was, waar, waarom] (meant waar); feitwlijk [feitelijk] (meant feitelijk); saaii [saai, saaie] (meant saai); hoore [hoor, hoorde, hoort] (meant hoorde); zuden [zouden, zuiden, zaden] (meant zouden); lerk [leek, kerk, lek] (meant leek); faan [gaan, aan, fan] (meant gaan); vinf [vind, vijf, ving] (meant vijf)
- tap EN usage, NL+EN, wrong: deno->denk (meant demo); doesm->does (meant doesn); neeed->need (meant needed); rpook->rook (meant took); ini->in (meant mini); nlow->now (meant blow); tko->to (meant too); pje->je (meant pie)
- tap EN usage, NL+EN, missed: adn [and, dan, an] (meant and); esrves [serves] (meant serves); deturm [] (meant return); eud [oud, eed, ed] (meant did); wih [with, wij, wish] (meant with); hten [then, haten, hen] (meant then); cuck [fuck, cuckoo, chuck] (meant fuck); hdwish [] (meant jewish)
- tap NL uniform, NL+EN, wrong: makke->make (meant makkie); pratten->praten (meant praatten); jaer->jaar (meant jager); veroorzaamt->veroorzaakt (meant veroorzaakte); hoogg->hoog (meant hogg); vehoord->gehoord (meant verhoord); mayr->maar (meant mary); gveolgen->gevolgen (meant gevlogen)
- tap NL uniform, NL+EN, missed: tatent [attent, talent, patent] (meant attent); opvieden [opvoeden] (meant opvoeden); ateelt [steelt, teelt] (meant steelt); allemschtig [allemachtig] (meant allemachtig); specialieit [specialiteit] (meant specialiteit); kaddy [maddy, daddy, paddy] (meant maddy); deuppel [druppel] (meant druppel); sttructuir [] (meant structuur)
- harness EN usage, EN (optimistic), wrong: glood->good (meant blood); desr->dear (meant deer); loh->oh (meant ooh); havej->have (meant haven); whre->where (meant whore); lotf->lot (meant loft); mce->me (meant mice); waht->what (meant want)
- harness EN usage, EN (optimistic), missed: vefa [vera, vega] (meant vega); ights [lights, nights, rights] (meant lights); przyers [prayers] (meant prayers); dlin [doin, lin, din] (meant doin); jome [home, joke, come] (meant joke); arres [arrest, arrested, arrests] (meant arrest); fw [few, fa, fe] (meant few); hshh [shh] (meant shhh)
- real EN curated, EN, missed: tommorow [] (meant tomorrow); wierd [weird, wired, wield] (meant weird); calender [calendar] (meant calendar); foward [forward, coward, toward] (meant forward); happend [happened, happens, happen] (meant happened); peice [piece, peace, price] (meant piece); truely [truly] (meant truly); helo [hello, help, hell] (meant hello)
- real EN Wikipedia, EN, wrong: adviced->advice (meant advised); atain->again (meant attain); casion->casino (meant caisson); cxan->can (meant cyan); dyas->days (meant dryas); efel->feel (meant evil); eles->else (meant eels); exerciese->exercise (meant exercises)
- real EN Wikipedia, EN, missed: aberation [] (meant aberration); abilityes [abilities] (meant abilities); abscence [absence] (meant absence); abondoning [abandoning] (meant abandoning); abondons [] (meant abandons); aborigene [] (meant aborigine); accesories [accessories] (meant accessories); abortificant [] (meant abortifacient)
- EN missing apostrophes, EN, missed: im [i'm, in, him] (meant i'm); wasnt [wasn't, want, wast] (meant wasn't)
- real NL curated, NL, missed: trouwes [trouwens, trouwe, trouwen] (meant trouwens); oko [ook, oké, oke] (meant ook); groejtes [groetjes] (meant groetjes); groetn [groeten, groten, groen] (meant groeten); eventeel [eventueel, evenveel] (meant eventueel); eevn [even, een] (meant even)
- real NL extra, NL, missed: wekren [werken, weken, weren] (meant werken); berigt [] (meant bericht); berichtej [berichten, berichtje] (meant berichtje); groetjse [groetjes] (meant groetjes); eetn [een, eten, eet] (meant eten); koffe [koffie, koffer, koffers] (meant koffie); betre [beter, betreft, betrek] (meant beter); mooii [mooi, mooie, moois] (meant mooi)
- real NL all, NL+EN, missed: trouwes [trouwens, trouwe, trouwen] (meant trouwens); oko [ook, oké, ok] (meant ook); hbe [heb, he, be] (meant heb); groejtes [groetjes] (meant groetjes); groetn [groeten, groten, groen] (meant groeten); eventeel [eventueel, evenveel] (meant eventueel); eevn [even, een] (meant even); wekren [werken, weken, weren] (meant werken)
