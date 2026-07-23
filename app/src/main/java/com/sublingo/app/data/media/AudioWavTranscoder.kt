package com.sublingo.app.data.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.AudioFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

class AudioWavTranscoder @Inject constructor() {
    fun toMono16KhzWav(input: File): ByteArray {
        require(input.isFile) { "音频分片不存在" }
        val extractor = MediaExtractor().apply { setDataSource(input.absolutePath) }
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run { extractor.release(); error("音频分片没有可解码的音轨") }
        val inputFormat = extractor.getTrackFormat(track)
        val mime = requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME))
        inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        extractor.selectTrack(track)
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(inputFormat, null, null, 0)
        codec.start()
        val pcm = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var outputFormat = inputFormat
        try {
            while (!outputEnded) {
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = requireNotNull(codec.getInputBuffer(index)).apply { clear() }
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime.coerceAtLeast(0L), extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    else -> if (index >= 0) {
                        codec.getOutputBuffer(index)?.let { buffer ->
                            if (info.size > 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                buffer.get(bytes)
                                pcm.write(bytes)
                            }
                        }
                        codec.releaseOutputBuffer(index, false)
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
        val sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val encoding = runCatching { outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) }.getOrDefault(2)
        require(encoding == 2) { "设备解码器返回了不支持的 PCM 编码：$encoding" }
        return PcmWavEncoder.mono16Khz(pcm.toByteArray(), sampleRate, channels)
    }

    private companion object { const val TIMEOUT_US = 10_000L }
}

object PcmWavEncoder {
    private const val TARGET_RATE = 16_000

    fun mono16Khz(pcm: ByteArray, sourceRate: Int, channels: Int): ByteArray {
        require(sourceRate > 0 && channels > 0) { "PCM 音频参数无效" }
        val shorts = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frameCount = shorts.remaining() / channels
        require(frameCount > 0) { "PCM 音频内容为空" }
        val outputFrames = (frameCount.toLong() * TARGET_RATE / sourceRate).toInt()
        val dataSize = outputFrames * 2
        val output = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        output.put("RIFF".toByteArray())
        output.putInt(36 + dataSize)
        output.put("WAVEfmt ".toByteArray())
        output.putInt(16)
        output.putShort(1)
        output.putShort(1)
        output.putInt(TARGET_RATE)
        output.putInt(TARGET_RATE * 2)
        output.putShort(2)
        output.putShort(16)
        output.put("data".toByteArray())
        output.putInt(dataSize)
        for (targetFrame in 0 until outputFrames) {
            val sourceFrame = (targetFrame.toLong() * sourceRate / TARGET_RATE).toInt().coerceAtMost(frameCount - 1)
            var sum = 0
            for (channel in 0 until channels) sum += shorts.get(sourceFrame * channels + channel).toInt()
            output.putShort((sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
        return output.array()
    }
}
