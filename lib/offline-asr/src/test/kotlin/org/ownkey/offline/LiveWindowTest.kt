package org.ownkey.offline

import kotlin.test.*

class LiveWindowTest {
    /** Words spoken every [gap] seconds from [from]. */
    private fun spoken(text: String, from: Double = 0.0, gap: Double = 0.4): List<TimedWord> =
        text.split(" ").mapIndexed { i, word -> TimedWord(word, from + i * gap) }

    @Test fun `tokens join into words at a leading space or word marker`() {
        val words = LiveWindow.words(arrayOf(" Hij", " bew", "oog", "▁zich", "."), floatArrayOf(0.48f, 1.12f, 1.2f, 1.6f, 1.9f), 2.0)
        assertEquals(listOf(TimedWord("Hij", 0.48), TimedWord("bewoog", 1.12), TimedWord("zich.", 1.6)), words.map { it.copy(start = Math.round(it.start * 100) / 100.0) })
    }

    @Test fun `missing timestamps are spread over the audio`() {
        val words = LiveWindow.words(arrayOf(" a", " b"), FloatArray(0), 2.0)
        assertEquals(listOf(0.0, 1.0), words.map { it.start })
    }

    @Test fun `a sentence end freezes once speech follows it in two previews`() {
        val window = LiveWindow()
        val first = window.accept(spoken("Hello world. This is"), audioEnd = 3.4)!!
        assertEquals(emptyList(), first.frozen)
        val second = window.accept(spoken("Hello world. This is fine"), audioEnd = 3.8)!!
        assertEquals(listOf("Hello", "world."), second.frozen)
        assertEquals(listOf("This", "is", "fine"), second.live)
    }

    @Test fun `a sentence end too close to the end of the audio does not count`() {
        val window = LiveWindow()
        window.accept(spoken("Hello world. This"), audioEnd = 1.5)
        val update = window.accept(spoken("Hello world. This is"), audioEnd = 1.6)!!
        assertEquals(emptyList(), update.frozen)
    }

    @Test fun `a full stop that only shows up once does not freeze`() {
        val window = LiveWindow()
        window.accept(spoken("Hello world, this is"), audioEnd = 3.0)
        val update = window.accept(spoken("Hello world. This is"), audioEnd = 3.0)!!
        assertEquals(emptyList(), update.frozen)
    }

    @Test fun `past twelve words without a sentence end the oldest words freeze until eight remain`() {
        val window = LiveWindow()
        val update = window.accept(spoken("one two three four five six seven eight nine ten eleven twelve thirteen"), audioEnd = 5.5)!!
        assertEquals(listOf("one", "two", "three", "four", "five"), update.frozen)
        assertEquals(8, update.live.size)
    }

    @Test fun `later decodes start on a frozen word about two seconds before the cut and drop what came before`() {
        val window = LiveWindow()
        window.accept(spoken("one two three four five six seven eight nine ten eleven twelve thirteen"), audioEnd = 5.5)
        // "five" started at 1.6 s and "six" at 2.0 s, so the cut is at 1.8 s and the decode starts before 0 s.
        assertEquals(0.0, window.decodeStart())
        val update = window.accept(spoken("four five six seven eight nine ten eleven twelve thirteen fourteen", from = 1.2), audioEnd = 6.0)!!
        assertEquals(emptyList(), update.frozen)
        assertEquals("six", update.live.first())
    }

    @Test fun `the decode start snaps to the start of a frozen word`() {
        val window = LiveWindow()
        val words = spoken("a b c d e f g h i j k l m n o p q r s t u", gap = 0.5)
        window.accept(words, audioEnd = 11.0)
        // 21 words: 13 froze, so the cut sits between "m" (6.0 s) and "n" (6.5 s) at 6.25 s; 2 s before is 4.25 s.
        assertEquals(3.9, window.decodeStart(), 1e-9)
    }

    @Test fun `the word after a frozen full stop gets a capital`() {
        val window = LiveWindow()
        window.accept(spoken("Hello world. This is"), audioEnd = 3.4)
        window.accept(spoken("Hello world. This is fine"), audioEnd = 3.8)
        val update = window.accept(spoken("world, this is fine", from = 0.4), audioEnd = 3.5)!!
        assertEquals("This", update.live.first())
    }

    @Test fun `previews hide the trailing full stop and the final text keeps it`() {
        val window = LiveWindow()
        val update = window.accept(spoken("Hij bewoog zich tans met groot."), audioEnd = 3.0)!!
        assertEquals("groot", update.live.last())
        assertEquals("groot.", window.finish(spoken("Hij bewoog zich tans met groot.")).frozen.last())
    }

    @Test fun `an empty preview changes nothing and the final text falls back to the last preview`() {
        val window = LiveWindow()
        window.accept(spoken("Hello there"), audioEnd = 1.0)
        assertNull(window.accept(emptyList(), audioEnd = 1.8))
        assertEquals(listOf("Hello", "there"), window.finish(emptyList()).frozen)
    }

    @Test fun `a long quiet stretch freezes the words and stops decoding the silence`() {
        val window = LiveWindow()
        window.accept(spoken("Hello there"), audioEnd = 2.0)
        val update = window.accept(spoken("Hello there"), audioEnd = 21.0)!!
        assertEquals(listOf("Hello", "there"), update.frozen)
        assertEquals(emptyList(), update.live)
        assertEquals(20.5 - 2.0, window.decodeStart(), 1e-9)
    }

    @Test fun `an empty decode over a long backlog still moves the window forward`() {
        val window = LiveWindow()
        window.accept(listOf(TimedWord("Hi", 0.4)), audioEnd = 1.0)
        val update = window.accept(emptyList(), audioEnd = 30.0)!!
        assertEquals(listOf("Hi"), update.frozen)
        assertEquals(26.0, window.decodeStart(), 1e-9)
        // Later decodes follow the end of the audio instead of going back to the beginning.
        assertNull(window.accept(emptyList(), audioEnd = 40.0))
        assertEquals(26.0, window.decodeStart(), 1e-9)
        assertNull(window.accept(emptyList(), audioEnd = 50.0))
        assertEquals(36.0, window.decodeStart(), 1e-9)
    }

    @Test fun `in a pause the first good reading settles and later drift is ignored`() {
        val window = LiveWindow()
        val spokenAt = spoken("Opmerkelijk zeer opmerkelijk", from = 4.0)
        assertEquals(emptyList(), window.accept(spokenAt, audioEnd = 5.5)!!.frozen)
        // Two previews agree and nothing new was said for two seconds: the reading becomes final.
        val settled = window.accept(spokenAt, audioEnd = 6.9)!!
        assertEquals(listOf("Opmerkelijk", "zeer", "opmerkelijk"), settled.frozen)
        assertEquals(emptyList(), settled.live)
        // More silence makes the decoder drift, as in the recording; none of that reaches the text.
        val drifted = listOf(TimedWord("Opmerlijk,", 4.0), TimedWord("sehr", 4.4), TimedWord("opmerkelijk", 4.8))
        assertNull(window.accept(drifted, audioEnd = 10.0))
        assertNull(window.accept(drifted, audioEnd = 13.0))
    }

    @Test fun `previews that keep disagreeing settle after three and a half seconds`() {
        val window = LiveWindow()
        window.accept(spoken("een twee"), audioEnd = 2.4)
        assertEquals(emptyList(), window.accept(spoken("een drie"), audioEnd = 2.6)!!.frozen)
        assertEquals(listOf("een", "vier"), window.accept(spoken("een vier"), audioEnd = 4.0)!!.frozen)
    }

    @Test fun `a full stop at a pause waits for what follows and goes away when the sentence goes on`() {
        val window = LiveWindow()
        window.accept(spoken("Ik wil graag."), audioEnd = 2.9)
        val settled = window.accept(spoken("Ik wil graag."), audioEnd = 3.0)!!
        assertEquals(listOf("Ik", "wil", "graag"), settled.frozen)
        // The next decode still covers "graag" and reads on without a full stop.
        val resumed = window.accept(listOf(TimedWord("graag", 0.8), TimedWord("naar", 5.2), TimedWord("huis", 5.6)), audioEnd = 6.2)!!
        assertEquals(emptyList(), resumed.frozen)
        assertEquals(listOf("naar", "huis"), resumed.live)
    }

    @Test fun `a word that began just before the settling preview ended is kept`() {
        val window = LiveWindow()
        window.accept(spoken("Ik wil graag."), audioEnd = 2.9)
        window.accept(spoken("Ik wil graag."), audioEnd = 3.0)
        // Speech resumed at 2.9 s, too late for the settling preview to write out "naar".
        val resumed = window.accept(listOf(TimedWord("graag", 0.8), TimedWord("naar", 2.9), TimedWord("huis.", 3.3)), audioEnd = 4.0)!!
        assertEquals(emptyList(), resumed.frozen)
        assertEquals(listOf("naar", "huis"), resumed.live)
        assertEquals(listOf("naar", "huis."), window.finish(listOf(TimedWord("graag", 0.8), TimedWord("naar", 2.9), TimedWord("huis.", 3.3))).frozen)
    }

    @Test fun `speech that resumed earlier in the settling preview counts once the speaker goes on`() {
        val window = LiveWindow()
        window.accept(spoken("Ik wil graag"), audioEnd = 2.9)
        window.accept(spoken("Ik wil graag"), audioEnd = 3.0)
        val resumed = window.accept(listOf(TimedWord("graag", 0.8), TimedWord("naar", 2.2), TimedWord("huis", 3.3)), audioEnd = 4.0)!!
        assertEquals(listOf("naar", "huis"), resumed.live)
        // Once back in, the word stays in the following previews too.
        val next = window.accept(listOf(TimedWord("graag", 0.8), TimedWord("naar", 2.2), TimedWord("huis", 3.3), TimedWord("toe", 3.9)), audioEnd = 4.6)!!
        assertEquals(listOf("naar", "huis", "toe"), next.live)
    }

    @Test fun `a word written out only after the settle still gets in`() {
        val window = LiveWindow()
        window.accept(spoken("innemend en"), audioEnd = 2.4)
        assertEquals(listOf("innemend", "en"), window.accept(spoken("innemend en"), audioEnd = 2.5)!!.frozen)
        // "minzaam" was said right after "en" but only appears in a later reading.
        val late = window.accept(listOf(TimedWord("en", 0.4), TimedWord("minzaam,", 0.7)), audioEnd = 3.3)!!
        assertEquals(listOf("minzaam,"), late.live)
        val other = LiveWindow()
        other.accept(spoken("innemend en"), audioEnd = 2.4)
        other.accept(spoken("innemend en"), audioEnd = 2.5)
        assertEquals(listOf("minzaam,"), other.finish(listOf(TimedWord("en", 0.4), TimedWord("minzaam,", 0.7))).frozen)
    }

    @Test fun `a late word timed close to the settled word still gets in and stays`() {
        val window = LiveWindow()
        // Timings from the test recording: Orukeet timed "en" and "minzaam" only 0.16 s apart.
        val settledReading = listOf(TimedWord("innemend", 4.4), TimedWord("en", 5.2))
        window.accept(settledReading, audioEnd = 7.3)
        assertEquals(listOf("innemend", "en"), window.accept(settledReading, audioEnd = 7.4)!!.frozen)
        val late = listOf(TimedWord("innemend", 4.4), TimedWord("en", 5.2), TimedWord("minzaam", 5.36))
        assertEquals(listOf("minzaam"), window.accept(late, audioEnd = 8.2)!!.live)
        // The next preview reads it again: it stays, and settles in turn.
        assertEquals(listOf("minzaam"), window.accept(late, audioEnd = 9.0)!!.frozen)
    }

    @Test fun `a later reading that splits the last settled word adds nothing`() {
        val window = LiveWindow()
        window.accept(spoken("een zware bonte"), audioEnd = 3.0)
        assertEquals(listOf("een", "zware", "bonte"), window.accept(spoken("een zware bonte"), audioEnd = 3.1)!!.frozen)
        assertNull(window.accept(listOf(TimedWord("zware", 0.4), TimedWord("bon", 0.8), TimedWord("te", 1.1)), audioEnd = 4.0))
    }

    @Test fun `a settled word read again a little later is not repeated`() {
        val window = LiveWindow()
        window.accept(spoken("een zware bonte"), audioEnd = 3.0)
        window.accept(spoken("een zware bonte"), audioEnd = 3.1)
        assertNull(window.accept(listOf(TimedWord("zware", 0.4), TimedWord("bonte", 0.98)), audioEnd = 4.0))
        assertNull(window.accept(listOf(TimedWord("zware", 0.4), TimedWord("bon", 0.98), TimedWord("te", 1.1)), audioEnd = 4.8))
        // The reading closest to where the settled word started is that word; the same word after it is new.
        val repeated = listOf(TimedWord("bon", 0.8), TimedWord("te", 0.9), TimedWord("bonte", 1.15), TimedWord("onder", 1.5))
        assertEquals(listOf("bonte", "onder"), window.finish(repeated).frozen)
    }

    @Test fun `a settled word split differently still decides its held full stop`() {
        val window = LiveWindow()
        window.accept(spoken("een zware bonte."), audioEnd = 2.9)
        window.accept(spoken("een zware bonte."), audioEnd = 3.0)
        val resumed = window.accept(listOf(TimedWord("bon", 0.8), TimedWord("te.", 1.1), TimedWord("onder", 4.0)), audioEnd = 5.0)!!
        assertEquals(listOf("."), resumed.frozen)
        assertEquals(listOf("Onder"), resumed.live)
    }

    @Test fun `new speech does not lend the settled word its punctuation`() {
        val window = LiveWindow()
        window.accept(spoken("ik zie overal."), audioEnd = 2.9)
        window.accept(spoken("ik zie overal."), audioEnd = 3.0)
        val resumed = window.accept(listOf(TimedWord("over", 0.8), TimedWord("al.", 4.0), TimedWord("onder", 4.4)), audioEnd = 5.0)!!
        assertEquals(emptyList(), resumed.frozen)
        assertEquals(listOf("al.", "onder"), resumed.live)
    }

    @Test fun `a word that continues the sentence after a pause keeps its small letter`() {
        val window = LiveWindow()
        window.accept(spoken("met groot gemak."), audioEnd = 2.9)
        window.accept(spoken("met groot gemak."), audioEnd = 3.0)
        window.accept(listOf(TimedWord("gemak", 0.8), TimedWord("onder", 4.0), TimedWord("Franks", 4.4)), audioEnd = 5.0)
        // A later reading flips the seam to a sentence end; the decision already made stands.
        val flipped = window.accept(listOf(TimedWord("gemak.", 0.8), TimedWord("Onder", 4.0), TimedWord("Franks", 4.4), TimedWord("kennissen", 4.8)), audioEnd = 5.8)!!
        assertEquals(listOf("onder", "Franks", "kennissen"), flipped.live)
        // Only the letter: punctuation the later reading puts after it stays.
        val final = window.finish(listOf(TimedWord("gemak.", 0.8), TimedWord("Onder.", 4.0), TimedWord("Daarna", 4.6)))
        assertEquals(listOf("onder.", "Daarna"), final.frozen)
    }

    @Test fun `a full stop at a pause stays when the decoder still ends the sentence there`() {
        val window = LiveWindow()
        window.accept(spoken("Ik wil graag."), audioEnd = 2.9)
        window.accept(spoken("Ik wil graag."), audioEnd = 3.0)
        val resumed = window.accept(listOf(TimedWord("graag.", 0.8), TimedWord("Daarna", 4.8), TimedWord("niet", 5.2)), audioEnd = 6.0)!!
        assertEquals(listOf("."), resumed.frozen)
        assertEquals("Daarna", resumed.live.first())
    }

    @Test fun `after a short pause the next decode reads the word with the held full stop again`() {
        val window = LiveWindow()
        val sentence = spoken("Hij bewoog zich tans met groot gemak.", from = 19.5)
        window.accept(sentence, audioEnd = 23.0)
        assertEquals("gemak", window.accept(sentence, audioEnd = 26.0)!!.frozen.last())
        // Two seconds before the cut is silence after "gemak"; the decode starts six words back, on "bewoog".
        assertEquals(19.9 - 0.1, window.decodeStart(), 1e-9)
        val resumed = window.accept(spoken("tans met groot gemak.", from = 20.7) + spoken("Onder Franks", from = 25.8), audioEnd = 26.8)!!
        assertEquals(listOf("."), resumed.frozen)
        assertEquals(listOf("Onder", "Franks"), resumed.live)
    }

    @Test fun `after a long pause a capital decides the held full stop, and Stop keeps it`() {
        val window = LiveWindow()
        window.accept(spoken("Klaar."), audioEnd = 2.1)
        window.accept(spoken("Klaar."), audioEnd = 2.2)
        assertNull(window.accept(emptyList(), audioEnd = 9.0))
        assertNull(window.accept(emptyList(), audioEnd = 10.0))
        // The window moved into the silence, so the decode no longer covers "Klaar".
        val resumed = window.accept(listOf(TimedWord("Nieuw", 9.5)), audioEnd = 10.8)!!
        assertEquals(listOf("."), resumed.frozen)
        val other = LiveWindow()
        other.accept(spoken("Klaar."), audioEnd = 2.1)
        other.accept(spoken("Klaar."), audioEnd = 2.2)
        assertEquals(listOf("."), other.finish(emptyList()).frozen)
    }

    @Test fun `quiet audio leaves the window only after two previews found it empty`() {
        val window = LiveWindow()
        window.accept(spoken("Klaar"), audioEnd = 2.0)
        window.accept(spoken("Klaar"), audioEnd = 2.1)
        assertNull(window.accept(emptyList(), audioEnd = 6.0))
        assertNull(window.accept(emptyList(), audioEnd = 7.0))
        // The previews at 6 s and 7 s both covered the audio up to 6 s; the cut keeps two seconds before
        // that, so a word from 4.5 s that neither wrote out yet survives.
        val resumed = window.accept(listOf(TimedWord("Nieuw", 4.5)), audioEnd = 7.5)!!
        assertEquals(listOf("Nieuw"), resumed.live)
    }

    @Test fun `quiet audio that wasn't decoded settles the live words once they are old enough`() {
        val window = LiveWindow()
        window.accept(spoken("een dandy"), audioEnd = 1.2)
        assertNull(window.quiet(audioEnd = 2.0))
        val settled = window.quiet(audioEnd = 2.8)!!
        assertEquals(listOf("een", "dandy"), settled.frozen)
        assertEquals(emptyList(), settled.live)
    }

    @Test fun `a long quiet stretch that wasn't decoded leaves the window`() {
        val window = LiveWindow()
        window.accept(spoken("Klaar"), audioEnd = 1.0)
        window.quiet(audioEnd = 2.5)
        assertNull(window.quiet(audioEnd = 9.0))
        assertEquals(9.0 - 2.0 - 2.0, window.decodeStart(), 1e-9)
    }

    @Test fun `more words left live after the cap than the cap allows is refused`() {
        assertFailsWith<IllegalArgumentException> { LiveWindow.Config(maxLiveWords = 4) }
    }

    @Test fun `after the words settle the next decode starts a few words back`() {
        val window = LiveWindow()
        val sentence = spoken("gekleed als een dandy innemend en minzaam", from = 2.0, gap = 0.5)
        window.accept(sentence, audioEnd = 6.0)
        assertEquals(7, window.accept(sentence, audioEnd = 7.2)!!.frozen.size)
        // Two seconds before the cut reaches only "minzaam"; the decode starts on "als", six words back.
        assertEquals(2.5 - 0.1, window.decodeStart(), 1e-9)
    }

    @Test fun `silence from the start is skipped without a word ever being heard`() {
        val window = LiveWindow()
        assertNull(window.accept(emptyList(), audioEnd = 25.0))
        assertEquals(23.0 - 2.0, window.decodeStart(), 1e-9)
    }
}
