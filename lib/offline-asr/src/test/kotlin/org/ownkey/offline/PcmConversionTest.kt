package org.ownkey.offline

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.*

class PcmConversionTest {
    private fun pcm(vararg values: Int): ByteArray = ByteBuffer.allocate(values.size * 2)
        .order(ByteOrder.LITTLE_ENDIAN).apply { values.forEach { putShort(it.toShort()) } }.array()
    @Test fun `PCM16 normalizes signs and mixes stereo to mono`() {
        assertContentEquals(floatArrayOf(-1f, 0f, 0.5f), AudioDecoder.pcm16ToMono16k(pcm(-32768, 0, 16384),16000,1))
        assertContentEquals(floatArrayOf(0f,0.5f), AudioDecoder.pcm16ToMono16k(pcm(-16384,16384,16384,16384),16000,2))
    }
    @Test fun `resampling preserves duration and rejects incomplete or excessive audio`() {
        assertEquals(160, AudioDecoder.pcm16ToMono16k(ByteArray(480*2),48000,1).size)
        assertEquals(160, AudioDecoder.pcm16ToMono16k(ByteArray(80*2),8000,1).size)
        assertFailsWith<IllegalArgumentException> { AudioDecoder.pcm16ToMono16k(byteArrayOf(1),16000,1) }
        assertFailsWith<IllegalArgumentException> { AudioDecoder.pcm16ToMono16k(ByteArray(16000*2*32),16000,1) }
        assertFailsWith<IllegalArgumentException> { AudioDecoder.pcm16ToMono16k(pcm(1),96000,1) }
    }
}
