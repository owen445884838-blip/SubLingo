package com.sublingo.app.data.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object MediaTrackMuxer {
    fun merge(video: File, audio: File, target: File): File {
        require(video.isFile && audio.isFile) { "待合并的视频或音频文件不存在" }
        target.parentFile?.mkdirs()
        target.delete()
        val videoExtractor = MediaExtractor().apply { setDataSource(video.absolutePath) }
        val audioExtractor = MediaExtractor().apply { setDataSource(audio.absolutePath) }
        val videoInputTrack = videoExtractor.findTrack("video/")
        val audioInputTrack = audioExtractor.findTrack("audio/")
        require(videoInputTrack >= 0 && audioInputTrack >= 0) { "无法识别待合并的音视频轨道" }
        val videoFormat = videoExtractor.getTrackFormat(videoInputTrack)
        val audioFormat = audioExtractor.getTrackFormat(audioInputTrack)
        val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val videoOutputTrack = muxer.addTrack(videoFormat)
            val audioOutputTrack = muxer.addTrack(audioFormat)
            muxer.start()
            copyTrack(videoExtractor, videoInputTrack, muxer, videoOutputTrack, videoFormat)
            copyTrack(audioExtractor, audioInputTrack, muxer, audioOutputTrack, audioFormat)
            muxer.stop()
        } finally {
            videoExtractor.release()
            audioExtractor.release()
            muxer.release()
        }
        require(target.length() > video.length()) { "合并后的媒体文件异常" }
        return target
    }

    private fun MediaExtractor.findTrack(prefix: String): Int = (0 until trackCount).firstOrNull {
        getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith(prefix) == true
    } ?: -1

    private fun copyTrack(
        extractor: MediaExtractor,
        inputTrack: Int,
        muxer: MediaMuxer,
        outputTrack: Int,
        format: MediaFormat,
    ) {
        extractor.selectTrack(inputTrack)
        val maxSize = runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrDefault(1024 * 1024)
        val buffer = ByteBuffer.allocate(maxSize.coerceAtLeast(1024 * 1024))
        val info = MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
            @Suppress("WrongConstant")
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(outputTrack, buffer, info)
            extractor.advance()
        }
        extractor.unselectTrack(inputTrack)
    }
}
