# Legacy scorer, default profile

- Tap-noise real-word rates (excluded from sets): EN usage 18.4%, EN uniform 5.7%, NL usage 17.4%, NL uniform 4.5%

| Set | n | Right | Wrong | Precision | Top-1 | Top-3 | Typed is dict word | p50 us | p95 us |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| tap EN usage, EN | 1500 | 0.0 | 0.0 | 100.0 | 65.6 | 75.3 | 0.0 | 35 | 121 |
| tap EN uniform, EN | 1500 | 0.0 | 0.0 | 100.0 | 72.9 | 83.0 | 0.0 | 27 | 61 |
| tap NL usage, NL | 1500 | 0.0 | 0.0 | 100.0 | 66.9 | 76.1 | 0.0 | 22 | 52 |
| tap NL uniform, NL | 1500 | 0.0 | 0.0 | 100.0 | 75.6 | 83.8 | 0.0 | 14 | 31 |
| tap NL usage, NL+EN | 1500 | 0.0 | 0.0 | 100.0 | 61.5 | 73.0 | 3.7 | 30 | 72 |
| tap EN usage, NL+EN | 1500 | 0.0 | 0.0 | 100.0 | 63.9 | 73.7 | 1.0 | 27 | 60 |
| tap NL uniform, NL+EN | 1500 | 0.0 | 0.0 | 100.0 | 71.2 | 81.9 | 0.9 | 24 | 51 |
| harness EN usage, EN (optimistic) | 1113 | 0.0 | 0.0 | 100.0 | 67.0 | 77.8 | 0.0 | 14 | 29 |
| real EN curated, EN | 100 | 0.0 | 0.0 | 100.0 | 64.0 | 76.0 | 13.0 | 15 | 29 |
| real EN Wikipedia, EN | 3941 | 0.0 | 0.0 | 100.0 | 55.8 | 62.0 | 1.0 | 10 | 20 |
| EN missing apostrophes, EN | 14 | 0.0 | 0.0 | 100.0 | 0.0 | 0.0 | 100.0 | 18 | 31 |
| real NL curated, NL | 60 | 0.0 | 0.0 | 100.0 | 70.0 | 88.3 | 8.3 | 20 | 31 |
| real NL extra, NL | 141 | 0.0 | 0.0 | 100.0 | 83.7 | 92.9 | 0.7 | 17 | 31 |
| real NL all, NL+EN | 201 | 0.0 | 0.0 | 100.0 | 75.6 | 89.6 | 3.0 | 21 | 41 |

| Clean text | Words | False corrections | Per 1,000 | Examples |
| --- | --- | --- | --- | --- |
| clean EN, EN | 7999 | 0 | 0.00 |  |
| clean NL, NL | 7993 | 0 | 0.00 |  |
| clean EN, NL+EN | 7999 | 0 | 0.00 |  |
| clean NL, NL+EN | 7993 | 0 | 0.00 |  |

| Out-of-dictionary set | n | Already in dictionary | Changed | Examples |
| --- | --- | --- | --- | --- |
| oov.txt, NL+EN | 294 | 86 | 0 |  |
| oov.txt, EN | 294 | 48 | 0 |  |

## Examples
- tap EN usage, EN, missed: adn [adnan] (meant and); cildren [children] (meant children); esrves [serves] (meant serves); deturm [] (meant return); llong [along, long] (meant long); eud [] (meant did); qbout [about, 'bout, bout] (meant about); wih [] (meant with)
- tap EN uniform, EN, missed: paramedivs [paramedics] (meant paramedics); tarely [barely, rarely] (meant rarely); approvaal [approval] (meant approval); confessiob [confession] (meant confession); sobdr [sober] (meant sober); conseqences [consequences] (meant consequences); nyways [anyways] (meant anyways); ppsts [posts] (meant posts)
- tap NL usage, NL, missed: waa [waar, waard, waarom] (meant waar); nite [niet, nate, site] (meant niet); iedreeen [iedereen] (meant iedereen); feitwlijk [feitelijk] (meant feitelijk); komtt [komst, komt] (meant komt); saaii [saaie, saai] (meant saai); hoore [hoort, moore, hoorn] (meant hoorde); niiet [niet] (meant niets)
- tap NL uniform, NL, missed: tatent [talent, attent, patent] (meant attent); opvieden [opvoeden] (meant opvoeden); ateelt [steelt, teelt] (meant steelt); allemschtig [allemachtig] (meant allemachtig); wapdn [wapen] (meant wapen); specialieit [specialiteit] (meant specialiteit); kaddy [maddy, paddy, daddy] (meant maddy); brennann [brennan] (meant brennan)
- tap NL usage, NL+EN, missed: waa [waa, waar, waah] (meant waar); nite [nite, niet, nate] (meant niet); iedreeen [iedereen] (meant iedereen); feitwlijk [feitelijk] (meant feitelijk); komtt [komst, komt] (meant komt); saaii [saaie, saai] (meant saai); hoore [moore, hoort, hoorn] (meant hoorde); niiet [niet] (meant niets)
- tap EN usage, NL+EN, missed: adn [adnan] (meant and); cildren [children] (meant children); esrves [serves] (meant serves); deturm [] (meant return); llong [along, long] (meant long); eud [] (meant did); qbout [about, 'bout, bout] (meant about); wih [] (meant with)
- tap NL uniform, NL+EN, missed: tatent [talent, patent, attent] (meant attent); opvieden [opvoeden] (meant opvoeden); ateelt [steelt, teelt] (meant steelt); allemschtig [allemachtig] (meant allemachtig); wapdn [wapen] (meant wapen); specialieit [specialiteit] (meant specialiteit); kaddy [daddy, paddy, maddy] (meant maddy); brennann [brennan] (meant brennan)
- harness EN usage, EN (optimistic), missed: wtih [with] (meant with); fhings [things] (meant things); yoou [you, yoon, yoo] (meant you); yhen [when, then, chen] (meant then); wnd [] (meant and); thlmas [thomas] (meant thomas); vefa [vera, vega] (meant vega); ights [lights, nights, rights] (meant lights)
- real EN curated, EN, missed: teh [tehran] (meant the); taht [that, tart, baht] (meant that); adn [adnan] (meant and); hte [] (meant the); waht [what, want, wait] (meant what); jsut [just, jut] (meant just); konw [know, kong, kono] (meant know); wiht [with, wilt, whit] (meant with)
- real EN Wikipedia, EN, missed: abandonned [abandoned] (meant abandoned); aberation [] (meant aberration); abilityes [abilities] (meant abilities); abilties [abilities] (meant abilities); abilty [ability] (meant ability); abondon [abandon] (meant abandon); abbout [about, abbott, abbot] (meant about); abotu [about, abou] (meant about)
- EN missing apostrophes, EN, missed: dont [dont, don, done] (meant don't); im [im, image, impact] (meant i'm); youre [youre, your, yours] (meant you're); thats [thats, that, that-] (meant that's); didnt [didnt, didn, didn`t] (meant didn't); doesnt [doesnt, doesn] (meant doesn't); isnt [isnt, isn, ist] (meant isn't); ive [ive, ives, iverson] (meant i've)
- real NL curated, NL, missed: mischien [mischien, misschien] (meant misschien); misschein [misschien] (meant misschien); eigelijk [eigelijk, eigenlijk] (meant eigenlijk); eigenlik [eigenlijk] (meant eigenlijk); natuurlik [natuurlijk] (meant natuurlijk); natuurljik [natuurlijk] (meant natuurlijk); gewon [gewond, gewone, gewonde] (meant gewoon); mrogen [morgen, drogen, mogen] (meant morgen)
- real NL extra, NL, missed: vandag [vandaag] (meant vandaag); vnadaag [vandaag] (meant vandaag); morhen [morgen, moren] (meant morgen); gistren [gisteren] (meant gisteren); gisteen [gisteren] (meant gisteren); volgemde [volgende] (meant volgende); vlgende [volgende] (meant volgende); weeek [week] (meant week)
- real NL all, NL+EN, missed: mischien [mischien, mischief, misschien] (meant misschien); misschein [misschien] (meant misschien); eigelijk [eigelijk, eigenlijk] (meant eigenlijk); eigenlik [eigenlijk] (meant eigenlijk); natuurlik [natuurlijk] (meant natuurlijk); natuurljik [natuurlijk] (meant natuurlijk); gewon [gewond, gewone, gewonde] (meant gewoon); mrogen [morgen, drogen, mogen] (meant morgen)
