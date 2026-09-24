# Noisy-channel scorer, default settings

- Tap-noise real-word rates (excluded from sets): EN usage 18.4%, EN uniform 5.7%, NL usage 17.4%, NL uniform 4.5%

| Set | n | Right | Wrong | Precision | Top-1 | Top-3 | Typed is dict word | p50 us | p95 us |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tap EN usage, EN | 1500 | 70.6 | 0.6 | 99.2 | 87.9 | 92.6 | 0.0 | 70 | 203 |
| tap EN uniform, EN | 1500 | 39.7 | 0.9 | 97.7 | 81.6 | 88.1 | 0.0 | 56 | 133 |
| tap NL usage, NL | 1500 | 68.5 | 0.4 | 99.4 | 86.9 | 90.7 | 0.0 | 53 | 193 |
| tap NL uniform, NL | 1500 | 44.1 | 0.8 | 98.2 | 83.1 | 87.5 | 0.0 | 34 | 97 |
| tap NL usage, NL+EN | 1500 | 60.3 | 0.7 | 98.8 | 85.4 | 89.9 | 3.7 | 42 | 127 |
| tap EN usage, NL+EN | 1500 | 59.5 | 0.6 | 99.0 | 85.5 | 92.0 | 1.0 | 43 | 114 |
| tap NL uniform, NL+EN | 1500 | 31.7 | 0.4 | 98.8 | 82.1 | 86.9 | 0.9 | 31 | 72 |
| harness EN usage, EN (optimistic) | 1113 | 70.4 | 0.9 | 98.7 | 92.9 | 99.6 | 0.0 | 28 | 69 |
| real EN curated, EN | 100 | 66.0 | 0.0 | 100.0 | 93.0 | 95.0 | 13.0 | 36 | 92 |
| real EN Wikipedia, EN | 3941 | 24.0 | 0.7 | 97.2 | 60.7 | 63.2 | 1.0 | 18 | 47 |
| EN missing apostrophes, EN | 14 | 0.0 | 0.0 | 100.0 | 0.0 | 0.0 | 100.0 | 25 | 102 |
| real NL curated, NL | 60 | 81.7 | 0.0 | 100.0 | 98.3 | 100.0 | 8.3 | 36 | 64 |
| real NL extra, NL | 141 | 85.8 | 0.0 | 100.0 | 93.6 | 97.9 | 0.7 | 32 | 65 |
| real NL all, NL+EN | 201 | 81.6 | 0.0 | 100.0 | 94.5 | 98.5 | 3.0 | 43 | 87 |

| Clean text | Words | False corrections | Per 1,000 | Examples |
| --- | --- | --- | --- | --- |
| clean EN, EN | 7999 | 0 | 0.00 |  |
| clean NL, NL | 7993 | 1 | 0.13 | raport->rapport |
| clean EN, NL+EN | 7999 | 1 | 0.13 | Rima->Prima |
| clean NL, NL+EN | 7993 | 1 | 0.13 | raport->rapport |

| Out-of-dictionary set | n | Already in dictionary | Changed | Examples |
| --- | --- | --- | --- | --- |
| oov.txt, NL+EN | 313 | 87 | 1 | async->sync |
| oov.txt, EN | 313 | 48 | 2 | Thijs->This, async->sync |

## Examples
- tap EN usage, EN, wrong: lnog->long (meant along); ini->in (meant mini); nlow->now (meant blow); tko->to (meant too); hde->he (meant she); aou->you (meant about); wya->way (meant away); alte->late (meant alter)
- tap EN usage, EN, missed: deturm [] (meant return); eud [end, feud, ed] (meant did); cuck [fuck, cuckoo, chuck] (meant fuck); hdwish [] (meant jewish); ahs [has, as, ah] (meant has); islans [island, islands] (meant island); uor [our, or, for] (meant our); hrad [hard, head, had] (meant hard)
- tap EN uniform, EN, wrong: beautifull->beautiful (meant beautifully); fuucker->fucker (meant fucked); cafr->car (meant cafe); wsighs->sighs (meant weighs); myd->my (meant mud); dlo->do (meant doo); spmetime->sometime (meant sometimes); lke->like (meant luke)
- tap EN uniform, EN, missed: paramedivs [paramedics] (meant paramedics); tarely [rarely, barely] (meant rarely); sobdr [sober] (meant sober); ppsts [posts] (meant posts); shamelesss [shameless] (meant shameless); minthly [monthly] (meant monthly); hoel [hotel, hole, joel] (meant hole); skiiing [skiing] (meant skiing)
- tap NL usage, NL, wrong: niiet->niet (meant niets); trn->ten (meant toen); stopp->stop (meant stoppen); reven->even (meant tegen); dta->dat (meant sta); blijen->blijven (meant blijken)
- tap NL usage, NL, missed: waa [was, waar, waarom] (meant waar); feitwlijk [feitelijk] (meant feitelijk); hoore [hoor, hoorde, hoort] (meant hoorde); zuden [zouden, zuiden, zaden] (meant zouden); lerk [leek, kerk, lek] (meant leek); faan [gaan, aan, fan] (meant gaan); vinf [vind, vijf, ving] (meant vijf); aeth [seth, beth, meth] (meant seth)
- tap NL uniform, NL, wrong: hoodd->hoofd (meant hood); ded->de (meant deed); pratten->praten (meant praatten); pzer->per (meant gozer); veroorzaamt->veroorzaakt (meant veroorzaakte); vean->van (meant evan); vehoord->gehoord (meant verhoord); lng->lang (meant ling)
- tap NL uniform, NL, missed: tatent [attent, talent, patent] (meant attent); opvieden [opvoeden] (meant opvoeden); kaddy [maddy, kady, addy] (meant maddy); deuppel [druppel] (meant druppel); sttructuir [] (meant structuur); resents [presents] (meant presents); mademoisellr [mademoiselle] (meant mademoiselle); bwoordeeld [beoordeeld] (meant beoordeeld)
- tap NL usage, NL+EN, wrong: niiet->niet (meant niets); abnd->and (meant band); dlo->do (meant dol); stopp->stop (meant stoppen); bda->bad (meant had); uhj->uh (meant hij); reven->even (meant tegen); daay->day (meant dat)
- tap NL usage, NL+EN, missed: waa [was, waar, waarom] (meant waar); nite [niet, nite, note] (meant niet); feitwlijk [feitelijk] (meant feitelijk); hoore [hoor, hoorde, hoort] (meant hoorde); zuden [zouden, zuiden, zaden] (meant zouden); lerk [leek, kerk, lek] (meant leek); faan [gaan, aan, fan] (meant gaan); vinf [vind, vijf, ving] (meant vijf)
- tap EN usage, NL+EN, wrong: deno->denk (meant demo); rpook->rook (meant took); ini->in (meant mini); nlow->now (meant blow); tko->to (meant too); pje->je (meant pie); wya->way (meant away); labd->land (meant labs)
- tap EN usage, NL+EN, missed: adn [and, dan, an] (meant and); esrves [serves] (meant serves); deturm [] (meant return); eud [ed, end, feud] (meant did); wih [with, wij, wish] (meant with); hten [then, haten, hen] (meant then); cuck [fuck, cuckoo, chuck] (meant fuck); hdwish [] (meant jewish)
- tap NL uniform, NL+EN, wrong: makke->make (meant makkie); pratten->praten (meant praatten); veroorzaamt->veroorzaakt (meant veroorzaakte); vehoord->gehoord (meant verhoord); gveolgen->gevolgen (meant gevlogen); drinen->drinken (meant dringen)
- tap NL uniform, NL+EN, missed: tatent [attent, talent, patent] (meant attent); opvieden [opvoeden] (meant opvoeden); ateelt [steelt, teelt] (meant steelt); allemschtig [allemachtig] (meant allemachtig); specialieit [specialiteit] (meant specialiteit); kaddy [maddy, daddy, kady] (meant maddy); deuppel [druppel] (meant druppel); sttructuir [] (meant structuur)
- harness EN usage, EN (optimistic), wrong: glood->good (meant blood); agi->ago (meant abi); desr->dear (meant deer); loh->oh (meant ooh); whre->where (meant whore); lotf->lot (meant loft); mce->me (meant mice); waht->what (meant want)
- harness EN usage, EN (optimistic), missed: vefa [vera, vega] (meant vega); ights [lights, nights, rights] (meant lights); przyers [prayers] (meant prayers); dlin [doin, lin, din] (meant doin); jome [home, joke, come] (meant joke); fw [few, fa, fe] (meant few); hshh [shhh, shh] (meant shhh); tuen [then, turn, tune] (meant then)
- real EN curated, EN, missed: definately [definitely] (meant definitely); seperate [seperate, separate] (meant separate); occured [occured, occurred] (meant occurred); untill [until, untill] (meant until); tommorow [tommorow] (meant tomorrow); wierd [weird, wired, wield] (meant weird); goverment [government, goverment] (meant government); accomodate [accommodate] (meant accommodate)
- real EN Wikipedia, EN, wrong: adviced->advice (meant advised); asign->sign (meant assign); atain->again (meant attain); casion->casino (meant caisson); cxan->can (meant cyan); devided->decided (meant divided); dyas->days (meant dryas); efel->feel (meant evil)
- real EN Wikipedia, EN, missed: aberation [] (meant aberration); abilityes [abilities] (meant abilities); abondon [abandon] (meant abandon); abscence [absence] (meant absence); abondoned [abandoned] (meant abandoned); abondoning [abandoning] (meant abandoning); abondons [] (meant abandons); aborigene [] (meant aborigine)
- EN missing apostrophes, EN, missed: dont [dont, don, don`t] (meant don't); im [in, him, i] (meant i'm); youre [your, youre, yours] (meant you're); thats [thats, that, that`s] (meant that's); didnt [didnt, didn, didn`t] (meant didn't); doesnt [doesnt, doesn] (meant doesn't); isnt [isnt, isn, ist] (meant isn't); ive [give, live, five] (meant i've)
- real NL curated, NL, missed: mischien [misschien, mischien] (meant misschien); eigelijk [eigenlijk, eigelijk] (meant eigenlijk); gewon [gewoon, gewond, gewone] (meant gewoon); alsjeblief [alsjeblief, alsjeblieft, asjeblief] (meant alsjeblieft); trouwes [trouwens, trouwe, trouwen] (meant trouwens); waneer [wanneer, waneer] (meant wanneer); heben [hebben, heben, heen] (meant hebben); groejtes [groetjes] (meant groetjes)
- real NL extra, NL, missed: wekren [werken, weken, weren] (meant werken); berigt [] (meant bericht); berichtej [berichten, berichtje] (meant berichtje); groetjse [groetjes] (meant groetjes); eetn [een, eten, eet] (meant eten); koffe [koffie, koffer, koffers] (meant koffie); helamaal [helemaal] (meant helemaal); betre [beter, betreft, betrek] (meant beter)
- real NL all, NL+EN, missed: mischien [misschien, mischien, mischief] (meant misschien); eigelijk [eigenlijk, eigelijk] (meant eigenlijk); gewon [gewoon, gewond, gewone] (meant gewoon); alsjeblief [alsjeblief, alsjeblieft, asjeblief] (meant alsjeblieft); trouwes [trouwens, trouwe, trouwen] (meant trouwens); waneer [wanneer, waneer, wander] (meant wanneer); oko [ook, ok, oké] (meant ook); hbe [heb, he, be] (meant heb)
