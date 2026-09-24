# Noisy-channel scorer, default settings

- Tap-noise real-word rates (excluded from sets): EN usage 18.4%, EN uniform 5.7%, NL usage 17.4%, NL uniform 4.5%, context EN 18.8%, context NL 18.5%

| Set | n | Right | Wrong | Precision | Top-1 | Top-3 | Typed is dict word | p50 us | p95 us |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tap EN usage, EN | 1500 | 70.4 | 0.8 | 98.9 | 88.8 | 93.7 | 0.0 | 56 | 201 |
| tap EN uniform, EN | 1500 | 44.2 | 1.2 | 97.4 | 82.5 | 90.3 | 0.0 | 68 | 191 |
| tap NL usage, NL | 1500 | 69.3 | 0.4 | 99.4 | 88.9 | 93.7 | 0.0 | 52 | 185 |
| tap NL uniform, NL | 1500 | 48.3 | 1.0 | 98.0 | 84.9 | 89.6 | 0.0 | 56 | 154 |
| tap NL usage, NL+EN | 1500 | 62.2 | 0.9 | 98.6 | 87.6 | 93.1 | 1.1 | 74 | 271 |
| tap EN usage, NL+EN | 1500 | 59.6 | 0.8 | 98.7 | 86.1 | 93.3 | 0.7 | 67 | 241 |
| tap NL uniform, NL+EN | 1500 | 38.6 | 0.6 | 98.5 | 83.9 | 89.6 | 0.6 | 87 | 264 |
| harness EN usage, EN (optimistic) | 1113 | 68.6 | 0.9 | 98.7 | 91.2 | 98.3 | 0.0 | 50 | 151 |
| real EN curated, EN | 100 | 82.0 | 0.0 | 100.0 | 96.0 | 97.0 | 0.0 | 37 | 86 |
| real EN Wikipedia, EN | 3941 | 29.1 | 0.8 | 97.3 | 65.8 | 68.4 | 0.1 | 52 | 141 |
| EN missing apostrophes, EN | 14 | 100.0 | 0.0 | 100.0 | 100.0 | 100.0 | 0.0 | 35 | 78 |
| real NL curated, NL | 60 | 90.0 | 0.0 | 100.0 | 100.0 | 100.0 | 0.0 | 31 | 82 |
| real NL extra, NL | 141 | 86.5 | 0.0 | 100.0 | 94.3 | 98.6 | 0.0 | 60 | 245 |
| real NL all, NL+EN | 201 | 86.1 | 0.0 | 100.0 | 95.0 | 99.0 | 0.0 | 55 | 209 |
| context EN, EN | 1557 | 77.9 | 0.8 | 99.0 | 91.5 | 93.4 | 0.0 | 42 | 127 |
| context NL, NL | 1564 | 69.1 | 1.0 | 98.6 | 87.0 | 90.8 | 0.0 | 55 | 152 |
| context NL, NL+EN | 1564 | 67.3 | 0.8 | 98.8 | 86.6 | 90.3 | 1.2 | 92 | 338 |
| context EN, NL+EN | 1557 | 75.7 | 0.8 | 99.0 | 91.2 | 93.2 | 1.3 | 88 | 302 |
| tap EN usage, EN, with taps | 1500 | 81.5 | 0.8 | 99.0 | 92.4 | 95.2 | 0.0 | 51 | 214 |
| tap EN uniform, EN, with taps | 1500 | 68.3 | 1.0 | 98.6 | 87.4 | 92.5 | 0.0 | 46 | 253 |
| tap NL usage, NL, with taps | 1500 | 79.7 | 0.4 | 99.5 | 92.1 | 94.8 | 0.0 | 50 | 232 |
| tap NL uniform, NL, with taps | 1500 | 71.4 | 1.0 | 98.6 | 88.5 | 91.8 | 0.0 | 52 | 277 |
| tap NL usage, NL+EN, with taps | 1500 | 75.6 | 0.9 | 98.9 | 91.1 | 94.3 | 1.1 | 63 | 361 |
| context EN, EN, with taps | 1557 | 84.2 | 0.8 | 99.1 | 93.4 | 94.6 | 0.0 | 50 | 192 |
| context NL, NL, with taps | 1564 | 77.3 | 1.1 | 98.6 | 90.0 | 92.3 | 0.0 | 55 | 225 |
| context NL, NL+EN, with taps | 1564 | 75.6 | 0.8 | 98.9 | 89.7 | 91.9 | 1.2 | 85 | 397 |
| context EN, EN, words before removed | 1557 | 66.2 | 0.5 | 99.2 | 88.6 | 93.1 | 0.0 | 47 | 145 |
| context NL, NL, words before removed | 1564 | 62.0 | 1.0 | 98.5 | 83.8 | 90.2 | 0.0 | 42 | 134 |
| run-together EN, EN | 1953 | 78.4 | 0.3 | 99.7 | 99.1 | 99.8 | 0.0 | 50 | 124 |
| run-together NL, NL | 1982 | 69.1 | 0.3 | 99.6 | 75.7 | 75.9 | 0.0 | 59 | 145 |
| run-together NL, NL+EN | 1982 | 68.8 | 0.2 | 99.7 | 75.8 | 76.3 | 0.2 | 94 | 231 |

| Clean text | Words | False corrections | Per 1,000 | Examples | Known word not first | Examples |
| --- | --- | --- | --- | --- | --- | --- |
| clean EN, EN | 7999 | 1 | 0.13 | trans->trains | 0.70% | end->and, in->on, on->in, Sami->same, form->from, stain->strain, Sami->same, Thai->that, bottled->bottles, apartments->apartment, Sami->same, write->right |
| clean NL, NL | 7993 | 0 | 0.00 |  | 0.83% | pek->plek, mok->mol, been->ben, Gij->hij, Tom->om, net->met, zij->zijn, volgend->volgende, loven->leven, hoeden->houden, wiet->wie, een->en |
| clean EN, NL+EN | 7999 | 1 | 0.13 | Rima->Prima | 0.50% | end->and, on->in, form->from, stain->staan, Thai->that, bottled->bottles, apartments->apartment, write->right, mein->mean, wood->would, LED->let, struggle->struggled |
| clean NL, NL+EN | 7993 | 0 | 0.00 |  | 0.85% | pek->plek, mok->mol, Gij->hij, Tom->om, net->met, zij->zijn, volgend->volgende, loven->leven, fokt->foot, hoeden->houden, wiet->wie, een->en |
| clean EN, EN, with taps | 7999 | 1 | 0.13 | trans->trains | 0.71% | end->and, in->on, on->in, Sami->same, form->from, stain->strain, Sami->same, Thai->that, bottled->bottles, apartments->apartment, Sami->same, write->right |
| clean NL, NL, with taps | 7993 | 0 | 0.00 |  | 0.84% | pek->plek, mok->mol, been->ben, Gij->hij, Tom->om, net->met, zij->zijn, volgend->volgende, loven->leven, hoeden->houden, wiet->wie, een->en |
| clean NL, NL+EN, with taps | 7993 | 0 | 0.00 |  | 0.93% | pek->plek, gestoken->gestolen, mok->mol, dichter->dochter, Gij->hij, Tom->om, net->met, zij->zijn, volgend->volgende, loven->leven, fokt->foot, hoeden->houden |

| Out-of-dictionary set | n | Already in dictionary | Changed | Examples |
| --- | --- | --- | --- | --- |
| oov.txt, NL+EN | 313 | 79 | 1 | mergen->morgen |
| oov.txt, EN | 313 | 29 | 2 | Thijs->This, standup->stand up |
| oov.txt, NL+EN, with taps | 313 | 79 | 1 | mergen->morgen |
| oov.txt, EN, with taps | 313 | 29 | 2 | Thijs->This, standup->stand up |
| oov.txt lowercase, no words before, EN | 313 | 29 | 7 | lieke->like, thijs->this, siem->seem, bakker->baker, prins->prints, async->sync, standup->stand up |
| oov.txt lowercase, after 'talk to', EN | 313 | 29 | 9 | joost->boost, lieke->like, thijs->this, hidde->hide, kees->keep, siem->seem, smit->sit, deno->deny, standup->stand up |
| oov.txt lowercase, after 'I went to the', EN | 313 | 29 | 9 | lieke->like, thijs->this, eline->line, mees->mess, bakker->baker, breda->bread, gboard->board, standup->stand up, joh->job |
| oov.txt lowercase, no words before, NL | 313 | 74 | 3 | deno->denk, async->sync, mergen->morgen |
| oov.txt lowercase, after 'ik ga naar de', NL | 313 | 74 | 4 | bosman->boeman, breda->brede, deno->denk, linter->winter |

| Real-word errors | n | Intended first | Intended in top 3 | Autocorrected |
| --- | --- | --- | --- | --- |
| real-word EN, EN | 59 | 71.2 | 98.3 | 0 |
| real-word NL, NL | 37 | 51.4 | 89.2 | 0 |
| real-word NL, NL+EN | 37 | 51.4 | 89.2 | 0 |

| Next word | Positions | First prediction | In first 3 |
| --- | --- | --- | --- |
| next word EN, EN | 12741 | 16.4 | 28.7 |
| next word NL, NL | 11504 | 13.0 | 23.8 |
| next word NL, NL+EN | 11504 | 13.0 | 23.8 |
| next word EN, EN, hand-written | 135 | 17.0 | 34.1 |
| next word NL, NL, hand-written | 62 | 22.6 | 33.9 |
| next word EN, EN, hand-written, frequency only | 135 | 2.2 | 5.2 |
| next word NL, NL, hand-written, frequency only | 62 | 0.0 | 4.8 |
| next word EN, EN, frequency only | 12741 | 2.2 | 9.9 |
| next word NL, NL, frequency only | 11504 | 1.5 | 6.0 |

## Examples
- real-word EN, EN, intended word not first: your [your, you're, you] (meant you're); your [your, you're, you] (meant you're); you're [you're, your, you've] (meant your); it's [it's, its, it'd] (meant its); there [there, there's, they're] (meant they're); their [their, there, they're] (meant there); to [to, tom, too] (meant too); to [to, tom, too] (meant too); to [to, tom, told] (meant too); to [to, too, today] (meant too); of [of, off, on] (meant off); quite [quite, quiet, quote] (meant quiet)
- real-word NL, NL, intended word not first: Wordt [wordt, word, worst] (meant word); vind [vind, vindt, vinden] (meant vindt); Vindt [vindt, vind, bindt] (meant vind); gebeurt [gebeurt, gebeurd, gebeurtenis] (meant gebeurd); gebeurd [gebeurd, gebeurde, gebeurt] (meant gebeurt); jou [jou, jouw, jouwe] (meant jouw); jouw [jouw, jou, jouwe] (meant jou); als [als, dan, alsof] (meant dan); u [u, uw, uit] (meant uw); verhuist [verhuist, verhuisd, verhuis] (meant verhuisd); antwoord [antwoord, antwoorden, antwoordt] (meant antwoordt); bied [bied, biedt, bieden] (meant biedt)
- real-word NL, NL+EN, intended word not first: Wordt [wordt, word, worst] (meant word); vind [vind, vindt, vinden] (meant vindt); Vindt [vindt, vind, bindt] (meant vind); gebeurt [gebeurt, gebeurd, gebeurtenis] (meant gebeurd); gebeurd [gebeurd, gebeurde, gebeurt] (meant gebeurt); jou [jou, jouw, jouwe] (meant jouw); jouw [jouw, jou, jouwe] (meant jou); als [als, dan, alsof] (meant dan); u [u, uw, up] (meant uw); verhuist [verhuist, verhuisd, verhuis] (meant verhuisd); antwoord [antwoord, antwoorden, antwoordt] (meant antwoordt); bied [bied, biedt, bieden] (meant biedt)
- tap EN usage, EN, wrong: doesm->does (meant doesn); neeed->need (meant needed); lnog->long (meant along); ini->in (meant mini); nlow->now (meant blow); tko->to (meant too); hde->he (meant she); aou->you (meant about)
- tap EN usage, EN, missed: deturm [detour] (meant return); eud [end, feud, ed] (meant did); cuck [fuck, cuckoo, chuck] (meant fuck); hdwish [wish, dish] (meant jewish); ahs [has, as, ah] (meant has); guyss [guys, guess, guy] (meant guys); islans [island, islands, slams] (meant island); uor [our, or, for] (meant our)
- tap EN uniform, EN, wrong: yees->yes (meant eyes); beautifull->beautiful (meant beautifully); fuucker->fucker (meant fucked); dond->done (meant fond); cafr->car (meant cafe); wsighs->sighs (meant weighs); dlo->do (meant doo); spmetime->sometime (meant sometimes)
- tap EN uniform, EN, missed: paramedivs [paramedics, paramedic] (meant paramedics); tarely [rarely, barely, truly] (meant rarely); sobdr [sober, sobs, sob] (meant sober); ppsts [posts, psst, pets] (meant posts); minthly [monthly, minty] (meant monthly); hoel [hotel, hole, joel] (meant hole); pilors [pilots, priors, piles] (meant pilots); psychologsit [psychologist] (meant psychologist)
- tap NL usage, NL, wrong: niiet->niet (meant niets); stopp->stop (meant stoppen); hddeb->heb (meant hadden); reven->even (meant tegen); dta->dat (meant sta); blijen->blijven (meant blijken)
- tap NL usage, NL, missed: waa [was, waar, waarom] (meant waar); feitwlijk [feitelijk] (meant feitelijk); saaii [saai, saaie, aai] (meant saai); hoore [hoor, hoorde, hoe] (meant hoorde); zuden [zouden, zuiden, zien] (meant zouden); lerk [leek, kerk, lek] (meant leek); faan [gaan, aan, fan] (meant gaan); vinf [vind, vijf, ving] (meant vijf)
- tap NL uniform, NL, wrong: hoodd->hoofd (meant hood); pratten->praten (meant praatten); pzer->per (meant gozer); veroorzaamt->veroorzaakt (meant veroorzaakte); hoogg->hoog (meant hogg); vean->van (meant evan); vehoord->gehoord (meant verhoord); lng->lang (meant ling)
- tap NL uniform, NL, missed: tatent [attent, talent, agent] (meant attent); opvieden [opvoeden] (meant opvoeden); kaddy [maddy, lady, kado] (meant maddy); deuppel [druppel] (meant druppel); sttructuir [structuur] (meant structuur); resents [presents, present, regent] (meant presents); mademoisellr [mademoiselle] (meant mademoiselle); bwoordeeld [beoordeeld, beoordeel] (meant beoordeeld)
- tap NL usage, NL+EN, wrong: niiet->niet (meant niets); abnd->and (meant band); dlo->do (meant dol); aint->ain't (meant saint); stopp->stop (meant stoppen); bda->bad (meant had); hddeb->heb (meant hadden); uhj->uh (meant hij)
- tap NL usage, NL+EN, missed: waa [was, waar, waarom] (meant waar); feitwlijk [feitelijk] (meant feitelijk); saaii [saai, saaie, said] (meant saai); hoore [hoor, hoorde, here] (meant hoorde); zuden [zouden, zuiden, zien] (meant zouden); lerk [leek, kerk, lek] (meant leek); faan [gaan, aan, fan] (meant gaan); vinf [vind, vijf, ving] (meant vijf)
- tap EN usage, NL+EN, wrong: deno->denk (meant demo); doesm->does (meant doesn); neeed->need (meant needed); rpook->rook (meant took); ini->in (meant mini); nlow->now (meant blow); tko->to (meant too); pje->je (meant pie)
- tap EN usage, NL+EN, missed: adn [and, dan, an] (meant and); esrves [serves, serve, nerves] (meant serves); deturm [deur, datum, detour] (meant return); eud [oud, eed, ed] (meant did); wih [with, wij, wish] (meant with); hten [then, haten, hen] (meant then); cuck [fuck, cuckoo, chuck] (meant fuck); hdwish [wish, dish] (meant jewish)
- tap NL uniform, NL+EN, wrong: makke->make (meant makkie); pratten->praten (meant praatten); veroorzaamt->veroorzaakt (meant veroorzaakte); hoogg->hoog (meant hogg); vehoord->gehoord (meant verhoord); mayr->maar (meant mary); gveolgen->gevolgen (meant gevlogen); drinen->drinken (meant dringen)
- tap NL uniform, NL+EN, missed: tatent [attent, talent, agent] (meant attent); opvieden [opvoeden] (meant opvoeden); ateelt [steelt, stelt, telt] (meant steelt); allemschtig [allemachtig] (meant allemachtig); specialieit [specialiteit, specialist] (meant specialiteit); kaddy [maddy, lady, daddy] (meant maddy); deuppel [druppel] (meant druppel); sttructuir [structuur] (meant structuur)
- harness EN usage, EN (optimistic), wrong: glood->good (meant blood); desr->dear (meant deer); loh->oh (meant ooh); havej->have (meant haven); whre->where (meant whore); lotf->lot (meant loft); mce->me (meant mice); waht->what (meant want)
- harness EN usage, EN (optimistic), missed: vefa [vera, vega, eva] (meant vega); ights [lights, nights, rights] (meant lights); przyers [prayers, prayer] (meant prayers); dlin [doin, lin, in] (meant doin); jome [home, joke, come] (meant joke); arres [arrest, arrested, arrests] (meant arrest); fw [few, fa, fe] (meant few); hshh [shh, heh, hush] (meant shhh)
- real EN curated, EN, missed: tommorow [tomorrow] (meant tomorrow); wierd [weird, wired, word] (meant weird); calender [calendar, slender, calder] (meant calendar); foward [forward, coward, toward] (meant forward); happend [happened, happens, happen] (meant happened); peice [piece, peace, price] (meant piece); truely [truly, true, rely] (meant truly); helo [hello, help, hell] (meant hello)
- real EN Wikipedia, EN, wrong: acustom->a custom (meant accustom); adviced->advice (meant advised); atain->again (meant attain); bandwith->band with (meant bandwidth); casion->casino (meant caisson); cxan->can (meant cyan); dyas->days (meant dryas); efel->feel (meant evil)
- real EN Wikipedia, EN, missed: aberation [abortion, abe ration] (meant aberration); abilityes [abilities, ability, ability es] (meant abilities); abscence [absence, obscene] (meant absence); abondoning [abandoning] (meant abandoning); abondons [abandon] (meant abandons); aborigene [] (meant aborigine); accesories [accessories] (meant accessories); abortificant [] (meant abortifacient)
- real NL curated, NL, missed: trouwes [trouwens, trouwe, trouwen] (meant trouwens); oko [ook, oké, oke] (meant ook); groejtes [groetjes, groente, groepjes] (meant groetjes); groetn [groeten, groten, groen] (meant groeten); eventeel [eventueel, evenveel] (meant eventueel); eevn [even, een, en] (meant even)
- real NL extra, NL, missed: wekren [werken, weken, weten] (meant werken); berigt [bright, buigt, berg] (meant bericht); berichtej [berichten, berichtje, bericht] (meant berichtje); groetjse [groetjes, groentje, grietje] (meant groetjes); eetn [een, eten, en] (meant eten); koffe [koffie, koffer, koffers] (meant koffie); betre [beter, betreft, betrek] (meant beter); mooii [mooi, mooie, kooi] (meant mooi)
- real NL all, NL+EN, missed: trouwes [trouwens, trouwe, trouwen] (meant trouwens); oko [ook, oké, ok] (meant ook); hbe [heb, he, be] (meant heb); groejtes [groetjes, groente, groepjes] (meant groetjes); groetn [groeten, groten, groen] (meant groeten); eventeel [eventueel, evenveel, event eel] (meant eventueel); eevn [even, een, en] (meant even); wekren [werken, weken, weten] (meant werken)
- context EN, EN, wrong: hasy->has (meant hasty); mny->my (meant many); thos->this (meant those); awter->water (meant lawyer); coplee->couple (meant complete); pwn->own (meant pen); cald->cold (meant calf); yhee->thee (meant the)
- context EN, EN, missed: cosnultant [consultant] (meant consultant); sausafee [sausage] (meant sausage); confedece [] (meant conference); searcb [search, serb, sear] (meant search); srrve [serve, save, sure] (meant serve); weere [were, we're, where] (meant were); learniimg [learning] (meant learning); fvor [for, favor, or] (meant favor)
- context NL, NL, wrong: kleiin->klein (meant kleine); wgen->wagen (meant eten); daat->dat (meant daar); llen->allen (meant alleen); gxaan->gaan (meant gedaan); zuwt->zult (meant ziet); wse->we (meant war); gpen->geen (meant gapen)
- context NL, NL, missed: ijn [zijn, in, mijn] (meant zijn); ou [hou, zou, oud] (meant zou); blssenn [bossen, blussen, bussen] (meant bossen); vestandig [verstandig] (meant verstandig); tebtamen [tezamen] (meant tentamen); vdrachting [drachtig] (meant verachting); rekeningne [rekeningen, rekening] (meant rekeningen); joog [hoog, nog, oog] (meant hoog)
- context NL, NL+EN, wrong: kleiin->klein (meant kleine); daat->dat (meant daar); llen->allen (meant alleen); zuwt->zult (meant ziet); wse->we (meant war); ohut->out (meant hout); gwst->gast (meant geest); noet->niet (meant moet)
- context NL, NL+EN, missed: ijn [zijn, in, mijn] (meant zijn); ou [hou, out, zou] (meant zou); blssenn [bossen, blussen, bussen] (meant bossen); vestandig [verstandig] (meant verstandig); tebtamen [tezamen] (meant tentamen); vdrachting [drachtig] (meant verachting); rekeningne [rekeningen, rekening] (meant rekeningen); joog [hoog, nog, oog] (meant hoog)
- context EN, NL+EN, wrong: hasy->has (meant hasty); bgen->ben (meant began); mny->my (meant many); thos->this (meant those); eenn->een (meant been); awter->water (meant lawyer); pwn->own (meant pen); cald->cold (meant calf)
- context EN, NL+EN, missed: cosnultant [consultant] (meant consultant); bega [began, begaan, begs] (meant began); sausafee [sausage] (meant sausage); confedece [] (meant conference); searcb [search, serb, sear] (meant search); srrve [serve, save, sure] (meant serve); weere [were, we're, weer] (meant were); learniimg [learning] (meant learning)
- tap EN usage, EN, with taps, wrong: rsx->rex (meant red); doesm->does (meant doesn); neeed->need (meant needed); lnog->long (meant along); hde->he (meant she); tbee->thee (meant the); aou->you (meant about); wya->way (meant away)
- tap EN usage, EN, with taps, missed: eud [did, dad, dud] (meant did); ahs [has, as, ah] (meant has); guyss [guys, guess, guy] (meant guys); uor [our, or, for] (meant our); hrad [hard, head, had] (meant hard); nto [not, to, into] (meant not); thne [then, the, tune] (meant then); hoomd [home, hood, good] (meant home)
- tap EN uniform, EN, with taps, wrong: yees->yes (meant eyes); beautifull->beautiful (meant beautifully); fuucker->fucker (meant fucked); puoned->phoned (meant phones); coigh->cough (meant coughs); cafr->car (meant cafe); rrrst->rest (meant arrest); dlo->do (meant doo)
- tap EN uniform, EN, with taps, missed: hoel [hotel, joel, hole] (meant hole); psychologsit [psychologist] (meant psychologist); ibvolvrment [involvement] (meant involvement); tranmsissiion [transmission] (meant transmission); chirs [chris, chairs, chirps] (meant chris); phrse [purse, phrase, pure] (meant phrase); blun [blunt, blunder, bluntly] (meant blunt); atrillery [artillery] (meant artillery)
- tap NL usage, NL, with taps, wrong: niiet->niet (meant niets); trn->ten (meant toen); stopp->stop (meant stoppen); hddeb->heb (meant hadden); wst->wat (meant wist); blijen->blijven (meant blijken)
- tap NL usage, NL, with taps, missed: waa [was, waar, waarom] (meant waar); saaii [saai, saaie, aai] (meant saai); hoore [hoor, hoorde, hoe] (meant hoorde); zuden [zouden, zuiden, zien] (meant zouden); lerk [leek, kerk, lek] (meant leek); vinf [vijf, vind, ging] (meant vijf); zt [zit, zat, zet] (meant zit); abnd [band, and, hand] (meant band)
- tap NL uniform, NL, with taps, wrong: hoodd->hoofd (meant hood); pratten->praten (meant praatten); pzer->per (meant gozer); veroorzaamt->veroorzaakt (meant veroorzaakte); hoogg->hoog (meant hogg); vean->van (meant evan); vehoord->gehoord (meant verhoord); lng->lang (meant ling)
- tap NL uniform, NL, with taps, missed: tatent [attent, talent, agent] (meant attent); sttructuir [structuur] (meant structuur); resents [presents, present, regent] (meant presents); buitenaarxea [] (meant buitenaardse); ientificere [] (meant identificeren); onbetrowbaar [onbetrouwbaar] (meant onbetrouwbaar); hsren [haren, heren, jaren] (meant haren); weggevne [weggeven] (meant weggeven)
- tap NL usage, NL+EN, with taps, wrong: niiet->niet (meant niets); abnd->and (meant band); dlo->do (meant dol); aint->ain't (meant saint); trn->ten (meant toen); stopp->stop (meant stoppen); bda->bad (meant had); hddeb->heb (meant hadden)
- tap NL usage, NL+EN, with taps, missed: waa [was, waar, waarom] (meant waar); saaii [saai, saaie, said] (meant saai); hoore [hoor, hoorde, here] (meant hoorde); zuden [zouden, zuiden, zien] (meant zouden); lerk [leek, kerk, lek] (meant leek); vinf [vijf, vind, ging] (meant vijf); zt [zit, zat, zet] (meant zit); daaoom [daarom, doom, droom] (meant daarom)
- context EN, EN, with taps, wrong: yied->tied (meant tired); mny->my (meant many); thos->this (meant those); raoming->raining (meant roaming); awter->water (meant lawyer); coplee->couple (meant complete); cald->cold (meant calf); yhee->thee (meant the)
- context EN, EN, with taps, missed: cosnultant [consultant] (meant consultant); sausafee [sausage, sausages] (meant sausage); confedece [] (meant conference); weere [were, we're, where] (meant were); fvor [for, favor, or] (meant favor); airllnae [airline] (meant airplane); arrguments [arguments, argument] (meant arguments); diozappeared [disappeared] (meant disappeared)
- context NL, NL, with taps, wrong: lreces->proces (meant precies); kleiin->klein (meant kleine); wgen->wagen (meant eten); llen->allen (meant alleen); gxaan->gaan (meant gedaan); zuwt->zult (meant ziet); wse->we (meant war); gpen->geen (meant gapen)
- context NL, NL, with taps, missed: ijn [zijn, in, mijn] (meant zijn); ou [hou, zou, oud] (meant zou); vestandig [verstandig] (meant verstandig); tebtamen [tezamen] (meant tentamen); vdrachting [drachtig] (meant verachting); rekeningne [rekeningen, rekening] (meant rekeningen); mtt [met, matt, me] (meant met); gecontroeerd [gecontroleerd] (meant gecontroleerd)
- context NL, NL+EN, with taps, wrong: kleiin->klein (meant kleine); llen->allen (meant alleen); zuwt->zult (meant ziet); wse->we (meant war); kln->kon (meant komen); ohut->out (meant hout); gwst->gast (meant geest); noet->niet (meant moet)
- context NL, NL+EN, with taps, missed: ijn [zijn, in, mijn] (meant zijn); ou [hou, out, zou] (meant zou); vestandig [verstandig] (meant verstandig); tebtamen [tezamen] (meant tentamen); vdrachting [drachtig] (meant verachting); rekeningne [rekeningen, rekening] (meant rekeningen); mtt [my, met, matt] (meant met); gecontroeerd [gecontroleerd] (meant gecontroleerd)
- context EN, EN, words before removed, wrong: bre->be (meant bee); nuo->no (meant into); likse->like (meant likes); akd->and (meant take); awter->water (meant lawyer); kving->king (meant going); awt->at (meant want); thhs->this (meant the)
- context EN, EN, words before removed, missed: cosnultant [consultant] (meant consultant); opwning [opening, owning, downing] (meant opening); hisstory [history, his story, hiss tory] (meant history); bega [began, begat, beg] (meant began); wantz [wants, want, went] (meant wants); instrictor [instructor] (meant instructor); sausafee [sausage] (meant sausage); evee [ever, eve, even] (meant ever)
- context NL, NL, words before removed, wrong: ascht->acht (meant wacht); kleiin->klein (meant kleine); verveet->vergeet (meant verveelt); daat->dat (meant daar); vermkoeden->vermoeden (meant vermoorden); mke->me (meant moe); gxaan->gaan (meant gedaan); olgend->volgend (meant volgende)
- context NL, NL, words before removed, missed: ijn [zijn, in, mijn] (meant zijn); ou [zou, oud, oude] (meant zou); blssenn [bossen, blussen, bussen] (meant bossen); vloeustof [vloeistof] (meant vloeistof); puut [put, puur, punt] (meant put); uzur [uur, zuur, vuur] (meant zuur); tebtamen [tezamen] (meant tentamen); vdrachting [drachtig] (meant verachting)
- run-together EN, EN, wrong: ofa->of (meant of a); mea->me (meant me a); astop->stop (meant a stop); mea->me (meant me a); unidorm->uniform (meant uni dorm)
- run-together EN, EN, missed: haspractical [has practical] (meant has practical); searchyou [search you] (meant search you); isit [is it, visit, i sit] (meant is it); rainfalls [rainfall, rain falls] (meant rain falls); policeofficer [police officer] (meant police officer); everwrite [ever write] (meant ever write); ownspeeches [own speeches] (meant own speeches); stingyperson [stingy person] (meant stingy person)
- run-together NL, NL, wrong: uvertellen->vertellen (meant u vertellen); ume->me (meant u me); uvoor->voor (meant u voor); uouder->ouder (meant u ouder); vondin->vonden (meant vond in)
- run-together NL, NL, missed: zijnooit [] (meant zij nooit); bestekappers [] (meant beste kappers); anderensamen [] (meant anderen samen); bossenbranden [] (meant bossen branden); vloeistofis [vloeistof] (meant vloeistof is); hoeveelheiddood [] (meant hoeveelheid dood); rundnog [] (meant rund nog); mannenmet [] (meant mannen met)
- run-together NL, NL+EN, wrong: uvertellen->vertellen (meant u vertellen); uvoor->voor (meant u voor); uouder->ouder (meant u ouder); vondin->vonden (meant vond in)
- run-together NL, NL+EN, missed: zijnooit [] (meant zij nooit); bestekappers [] (meant beste kappers); anderensamen [] (meant anderen samen); bossenbranden [] (meant bossen branden); vloeistofis [vloeistof] (meant vloeistof is); hoeveelheiddood [] (meant hoeveelheid dood); rundnog [] (meant rund nog); mannenmet [] (meant mannen met)
