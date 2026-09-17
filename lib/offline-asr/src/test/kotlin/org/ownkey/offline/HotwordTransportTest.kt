package org.ownkey.offline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HotwordTransportTest {
    @Test
    fun joinsTermsWithSlashesAndTreatsUnsafeCharactersAsSpaces() {
        val encoded = HotwordTransport.encode(listOf("Ownkey", "Bart van der Meeren", "TCP/IP", "Note:\tone\nline", "bell"))
        assertEquals("Ownkey/Bart van der Meeren/TCP IP/Note one line/bell", encoded.hotwords)
        assertEquals(5, encoded.included)
        assertEquals(0, encoded.dropped)
    }

    @Test
    fun countsUntransportableAndCollidingTermsAsDroppedWithoutStoppingTheRest() {
        // Blank input is not a saved word and is ignored; a term that sanitises to nothing or that
        // collides with an earlier one cannot reach the recogniser and is reported as dropped, while
        // later terms still go through.
        val encoded = HotwordTransport.encode(listOf("Orukeet", " ", "orukeet", "/", "TCP/IP", "TCP:IP", "Voxtral"))
        assertEquals("Orukeet/TCP IP/Voxtral", encoded.hotwords)
        assertEquals(3, encoded.included)
        assertEquals(3, encoded.dropped)
    }

    @Test
    fun keepsAccentsAndStopsAtTheBudgetWithoutSplittingATerm() {
        val encoded = HotwordTransport.encode(listOf("Zoë", "Müller", "Ångström"), maxBytes = 12)
        // "Zoë" is 4 bytes, "/Müller" is 8 bytes, so the third term no longer fits.
        assertEquals("Zoë/Müller", encoded.hotwords)
        assertEquals(2, encoded.included)
        assertEquals(1, encoded.dropped)
        assertTrue(encoded.hotwords.toByteArray(Charsets.UTF_8).size <= 12)
    }

    @Test
    fun laterTermsAfterAnOverflowAreCountedAsDroppedEvenWhenShort() {
        val encoded = HotwordTransport.encode(listOf("abcdefgh", "ijklmnop", "a"), maxBytes = 10)
        assertEquals("abcdefgh", encoded.hotwords)
        assertEquals(1, encoded.included)
        assertEquals(2, encoded.dropped)
    }

    @Test
    fun emptyVocabularyEncodesToNothing() {
        val encoded = HotwordTransport.encode(emptyList())
        assertTrue(encoded.isEmpty)
        assertEquals(0, encoded.included)
    }
}
