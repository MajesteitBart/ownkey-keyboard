# Noisy-channel scorer, default settings

- Tap-noise real-word rates (excluded from sets): EN usage 18.4%, EN uniform 5.7%, NL usage 17.4%, NL uniform 4.5%, context EN 18.8%, context NL 18.5%

| Set | n | Right | Wrong | Precision | Top-1 | Top-3 | Typed is dict word | p50 us | p95 us |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tap EN usage, EN | 1500 | 70.5 | 0.7 | 99.0 | 86.9 | 91.5 | 0.0 | 59 | 181 |
| tap EN uniform, EN | 1500 | 44.5 | 1.1 | 97.5 | 80.3 | 87.5 | 0.0 | 53 | 187 |
| tap NL usage, NL | 1500 | 69.3 | 0.3 | 99.5 | 86.6 | 90.6 | 0.0 | 58 | 222 |
| tap NL uniform, NL | 1500 | 48.3 | 1.1 | 97.8 | 82.4 | 86.9 | 0.0 | 44 | 113 |
| tap NL usage, NL+EN | 1500 | 62.3 | 0.8 | 98.7 | 85.3 | 90.1 | 1.1 | 89 | 324 |
| tap EN usage, NL+EN | 1500 | 59.7 | 0.8 | 98.7 | 84.4 | 91.3 | 0.7 | 68 | 213 |
| tap NL uniform, NL+EN | 1500 | 38.6 | 0.7 | 98.3 | 81.4 | 86.9 | 0.6 | 56 | 138 |
| harness EN usage, EN (optimistic) | 1113 | 69.1 | 0.9 | 98.7 | 91.6 | 98.4 | 0.0 | 42 | 107 |
| real EN curated, EN | 100 | 82.0 | 0.0 | 100.0 | 95.0 | 95.0 | 0.0 | 51 | 110 |
| real EN Wikipedia, EN | 3941 | 29.3 | 0.7 | 97.6 | 62.4 | 64.1 | 0.1 | 36 | 73 |
| EN missing apostrophes, EN | 14 | 100.0 | 0.0 | 100.0 | 100.0 | 100.0 | 0.0 | 36 | 126 |
| real NL curated, NL | 60 | 90.0 | 0.0 | 100.0 | 100.0 | 100.0 | 0.0 | 46 | 90 |
| real NL extra, NL | 141 | 86.5 | 0.0 | 100.0 | 94.3 | 97.9 | 0.0 | 55 | 127 |
| real NL all, NL+EN | 201 | 86.1 | 0.0 | 100.0 | 95.5 | 98.5 | 0.0 | 76 | 186 |
| context EN, EN | 1557 | 77.1 | 0.6 | 99.2 | 88.5 | 90.2 | 0.0 | 56 | 168 |
| context NL, NL | 1564 | 68.6 | 0.9 | 98.7 | 85.5 | 88.7 | 0.0 | 57 | 169 |
| context NL, NL+EN | 1564 | 66.7 | 0.8 | 98.8 | 85.2 | 88.5 | 1.2 | 118 | 365 |
| context EN, NL+EN | 1557 | 75.3 | 0.8 | 99.0 | 88.4 | 90.1 | 1.3 | 118 | 357 |
| context EN, EN, words before removed | 1557 | 66.3 | 0.5 | 99.2 | 85.8 | 89.9 | 0.0 | 43 | 116 |
| context NL, NL, words before removed | 1564 | 62.1 | 1.0 | 98.5 | 82.4 | 88.3 | 0.0 | 26 | 82 |

| Clean text | Words | False corrections | Per 1,000 | Examples | Known word not first | Examples |
| --- | --- | --- | --- | --- | --- | --- |
| clean EN, EN | 7999 | 1 | 0.13 | trans->trains | 0.70% | end->and, in->on, on->in, Sami->same, form->from, stain->strain, Sami->same, Thai->that, bottled->bottles, apartments->apartment, Sami->same, write->right |
| clean NL, NL | 7993 | 0 | 0.00 |  | 0.83% | pek->plek, mok->mol, been->ben, Gij->hij, Tom->om, net->met, zij->zijn, volgend->volgende, loven->leven, hoeden->houden, wiet->wie, een->en |
| clean EN, NL+EN | 7999 | 1 | 0.13 | Rima->Prima | 0.50% | end->and, on->in, form->from, stain->staan, Thai->that, bottled->bottles, apartments->apartment, write->right, mein->mean, wood->would, LED->let, struggle->struggled |
| clean NL, NL+EN | 7993 | 0 | 0.00 |  | 0.85% | pek->plek, mok->mol, Gij->hij, Tom->om, net->met, zij->zijn, volgend->volgende, loven->leven, fokt->foot, hoeden->houden, wiet->wie, een->en |

| Out-of-dictionary set | n | Already in dictionary | Changed | Examples |
| --- | --- | --- | --- | --- |
| oov.txt, NL+EN | 313 | 79 | 1 | mergen->morgen |
| oov.txt, EN | 313 | 29 | 1 | Thijs->This |

| Real-word errors | n | Intended first | Intended in top 3 | Autocorrected |
| --- | --- | --- | --- | --- |
| real-word EN, EN | 59 | 71.2 | 98.3 | 0 |
| real-word NL, NL | 37 | 51.4 | 89.2 | 0 |
| real-word NL, NL+EN | 37 | 51.4 | 89.2 | 0 |

| Next word | Positions | First prediction | In first 3 |
| --- | --- | --- | --- |
| next word EN, EN | 12741 | 2.2 | 9.9 |
| next word NL, NL | 11504 | 1.5 | 6.0 |

## Examples
- real-word EN, EN, intended word not first: your [your, you're, you] (meant you're); your [your, you're, you] (meant you're); you're [you're, your, you've] (meant your); it's [it's, its, it'd] (meant its); there [there, there's, they're] (meant they're); their [their, there, they're] (meant there); to [to, too, tom] (meant too); to [to, tom, too] (meant too); to [to, tom, told] (meant too); to [to, too, today] (meant too); of [of, off, on] (meant off); quite [quite, quiet, quote] (meant quiet)
- real-word NL, NL, intended word not first: Wordt [wordt, word, worst] (meant word); vind [vind, vindt, vinden] (meant vindt); Vindt [vindt, vind, bindt] (meant vind); gebeurt [gebeurt, gebeurd, gebeurtenis] (meant gebeurd); gebeurd [gebeurd, gebeurde, gebeurt] (meant gebeurt); jou [jou, jouw, jouwe] (meant jouw); jouw [jouw, jou, jouwe] (meant jou); als [als, dan, alsof] (meant dan); u [u, uw, uit] (meant uw); verhuist [verhuist, verhuisd, verhuis] (meant verhuisd); antwoord [antwoord, antwoorden, antwoordt] (meant antwoordt); bied [bied, biedt, bieden] (meant biedt)
- real-word NL, NL+EN, intended word not first: Wordt [wordt, word, worst] (meant word); vind [vind, vindt, vinden] (meant vindt); Vindt [vindt, vind, bindt] (meant vind); gebeurt [gebeurt, gebeurd, gebeurtenis] (meant gebeurd); gebeurd [gebeurd, gebeurde, gebeurt] (meant gebeurt); jou [jou, jouw, jouwe] (meant jouw); jouw [jouw, jou, jouwe] (meant jou); als [als, dan, alsof] (meant dan); u [u, uw, up] (meant uw); verhuist [verhuist, verhuisd, verhuis] (meant verhuisd); antwoord [antwoord, antwoorden, antwoordt] (meant antwoordt); bied [bied, biedt, bieden] (meant biedt)
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
- real NL curated, NL, missed: trouwes [trouwens, trouwe, trouwen] (meant trouwens); oko [ook, oké, oke] (meant ook); groejtes [groetjes] (meant groetjes); groetn [groeten, groten, groen] (meant groeten); eventeel [eventueel, evenveel] (meant eventueel); eevn [even, een] (meant even)
- real NL extra, NL, missed: wekren [werken, weken, weren] (meant werken); berigt [] (meant bericht); berichtej [berichten, berichtje] (meant berichtje); groetjse [groetjes] (meant groetjes); eetn [een, eten, eet] (meant eten); koffe [koffie, koffer, koffers] (meant koffie); betre [beter, betreft, betrek] (meant beter); mooii [mooi, mooie, moois] (meant mooi)
- real NL all, NL+EN, missed: trouwes [trouwens, trouwe, trouwen] (meant trouwens); oko [ook, oké, ok] (meant ook); hbe [heb, he, be] (meant heb); groejtes [groetjes] (meant groetjes); groetn [groeten, groten, groen] (meant groeten); eventeel [eventueel, evenveel] (meant eventueel); eevn [even, een] (meant even); wekren [werken, weken, weren] (meant werken)
- context EN, EN, wrong: hasy->has (meant hasty); mny->my (meant many); thos->this (meant those); awter->water (meant lawyer); pwn->own (meant pen); cald->cold (meant calf); yhee->thee (meant the); awt->at (meant want)
- context EN, EN, missed: cosnultant [consultant] (meant consultant); sausafee [] (meant sausage); confedece [] (meant conference); searcb [search] (meant search); srrve [serve] (meant serve); weere [were, we're, where] (meant were); learniimg [] (meant learning); fvor [for, favor] (meant favor)
- context NL, NL, wrong: kleiin->klein (meant kleine); wgen->wagen (meant eten); daat->dat (meant daar); llen->allen (meant alleen); gxaan->gaan (meant gedaan); zuwt->zult (meant ziet); wse->we (meant war); gpen->geen (meant gapen)
- context NL, NL, missed: ijn [zijn, in, mijn] (meant zijn); ou [hou, zou, oud] (meant zou); blssenn [] (meant bossen); vestandig [verstandig] (meant verstandig); tebtamen [] (meant tentamen); vdrachting [] (meant verachting); rekeningne [rekeningen] (meant rekeningen); joog [hoog, oog, jong] (meant hoog)
- context NL, NL+EN, wrong: kleiin->klein (meant kleine); daat->dat (meant daar); llen->allen (meant alleen); gxaan->gaan (meant gedaan); zuwt->zult (meant ziet); wse->we (meant war); ohut->out (meant hout); gwst->gast (meant geest)
- context NL, NL+EN, missed: ijn [zijn, in, mijn] (meant zijn); ou [hou, out, zou] (meant zou); blssenn [] (meant bossen); vestandig [verstandig] (meant verstandig); tebtamen [] (meant tentamen); vdrachting [] (meant verachting); rekeningne [rekeningen] (meant rekeningen); joog [hoog, oog, jong] (meant hoog)
- context EN, NL+EN, wrong: hasy->has (meant hasty); bgen->ben (meant began); mny->my (meant many); thos->this (meant those); eenn->een (meant been); awter->water (meant lawyer); pwn->own (meant pen); cald->cold (meant calf)
- context EN, NL+EN, missed: cosnultant [consultant] (meant consultant); bega [began, begaan, begs] (meant began); sausafee [] (meant sausage); confedece [] (meant conference); searcb [search] (meant search); srrve [serve] (meant serve); weere [were, we're, weer] (meant were); learniimg [] (meant learning)
- context EN, EN, words before removed, wrong: bre->be (meant bee); nuo->no (meant into); likse->like (meant likes); akd->and (meant take); awter->water (meant lawyer); kving->king (meant going); awt->at (meant want); thhs->this (meant the)
- context EN, EN, words before removed, missed: cosnultant [consultant] (meant consultant); opwning [opening, owning] (meant opening); bega [began, begat, beg] (meant began); wantz [wants, want, waltz] (meant wants); instrictor [instructor] (meant instructor); sausafee [] (meant sausage); evee [ever, eve, even] (meant ever); completde [complete, completed] (meant completed)
- context NL, NL, words before removed, wrong: ascht->acht (meant wacht); kleiin->klein (meant kleine); verveet->vergeet (meant verveelt); daat->dat (meant daar); vermkoeden->vermoeden (meant vermoorden); mke->me (meant moe); gxaan->gaan (meant gedaan); olgend->volgend (meant volgende)
- context NL, NL, words before removed, missed: ijn [zijn, in, mijn] (meant zijn); ou [zou, oud, oude] (meant zou); blssenn [] (meant bossen); vloeustof [vloeistof] (meant vloeistof); puut [put, puur, punt] (meant put); uzur [uur, zuur] (meant zuur); tebtamen [] (meant tentamen); vdrachting [] (meant verachting)
