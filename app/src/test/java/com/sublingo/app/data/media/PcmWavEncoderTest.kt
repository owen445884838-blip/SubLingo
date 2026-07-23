package com.sublingo.app.data.media

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmWavEncoderTest {
    @Test fun stereo48KhzBecomesMono16KhzWav() {
        val pcm = ByteBuffer.allocate(48_000 * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(48_000) { pcm.putShort(1_000); pcm.putShort(3_000) }
        val wav = PcmWavEncoder.mono16Khz(pcm.array(), 48_000, 2)
        assertEquals("RIFF", String(wav.copyOfRange(0, 4)))
        assertEquals("WAVE", String(wav.copyOfRange(8, 12)))
        assertEquals(16_000, ByteBuffer.wrap(wav, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        assertEquals(32_044, wav.size)
        assertEquals(2_000, ByteBuffer.wrap(wav, 44, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt())
    }
}
