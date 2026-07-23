package com.sublingo.app.data.media

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaMetadataRetriever
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class AudioSegment(val index: Int, val file: File, val startOffsetMs: Long, val durationMs: Long)

class AudioSegmenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val youtubeDlRuntime: YoutubeDlRuntime,
) {
    fun split(input: File, outputDir: File, segmentDurationMs: Long = 15 * 60_000L): List<AudioSegment> {
        require(input.isFile) { "媒体文件不存在" }
        outputDir.mkdirs()
        return runCatching {
            splitWithMediaExtractor(input, outputDir, segmentDurationMs)
        }.onFailure { error ->
            Log.w(TAG, "MediaExtractor could not read audio; falling back to FFmpeg", error)
        }.getOrElse { extractorError ->
            val capabilities = youtubeDlRuntime.capabilities()
            check(capabilities.ffmpegAvailable) {
                "当前设备使用 ${capabilities.pageSizeBytes / 1024}KB 内存页，内置 FFmpeg 不兼容；" +
                    "且 Android 无法读取该媒体的音频轨道。请改用带 AAC 音轨的 MP4，或重新下载该视频。" +
                    "（${extractorError.message ?: "未知媒体格式"}）"
            }
            splitWithFfmpeg(input, outputDir, segmentDurationMs)
        }
    }

    private fun splitWithMediaExtractor(input: File, outputDir: File, segmentDurationMs: Long): List<AudioSegment> {
        val probe = MediaExtractor().apply { setDataSource(input.absolutePath) }
        val audioTrack = (0 until probe.trackCount).firstOrNull {
            probe.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run { probe.release(); error("Android 无法识别媒体音频轨道") }
        val format = probe.getTrackFormat(audioTrack)
        val durationUs = format.getLong(MediaFormat.KEY_DURATION)
        probe.release()
        val segments = mutableListOf<AudioSegment>()
        var startUs = 0L
        var index = 0
        while (startUs < durationUs) {
            val endUs = minOf(durationUs, startUs + segmentDurationMs * 1_000)
            val target = File(outputDir, "chunk-${index.toString().padStart(3, '0')}.m4a.part")
            target.delete()
            muxRange(input, audioTrack, format, startUs, endUs, target)
            val committed = File(outputDir, target.name.removeSuffix(".part"))
            check(target.renameTo(committed)) { "无法提交音频分片" }
            segments += AudioSegment(index, committed, startUs / 1_000, (endUs - startUs) / 1_000)
            startUs = endUs
            index++
        }
        return segments
    }

    private fun splitWithFfmpeg(input: File, outputDir: File, segmentDurationMs: Long): List<AudioSegment> {
        outputDir.listFiles().orEmpty().filter { it.name.startsWith("chunk-") }.forEach(File::delete)
        val ffmpeg = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        require(ffmpeg.isFile) { "FFmpeg 组件不存在，请重新安装应用" }
        val pattern = File(outputDir, "chunk-%03d.part.m4a")
        val command = listOf(
            ffmpeg.absolutePath,
            "-hide_banner", "-loglevel", "warning", "-y",
            "-i", input.absolutePath,
            "-map", "0:a:0", "-vn",
            "-ac", "1", "-ar", "16000", "-c:a", "aac", "-b:a", "48k",
            "-f", "segment", "-segment_time", (segmentDurationMs / 1000).toString(),
            "-reset_timestamps", "1", pattern.absolutePath,
        )
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor(20, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            "FFmpeg 音频提取超时"
        }
        check(process.exitValue() == 0) { "FFmpeg 无法提取音频：${output.takeLast(600)}" }
        val parts = outputDir.listFiles().orEmpty()
            .filter { it.name.matches(Regex("chunk-\\d{3}\\.part\\.m4a")) && it.length() > 0L }
            .sortedBy(File::getName)
        require(parts.isNotEmpty()) { "媒体确实不包含可用音频轨道" }
        val totalDuration = mediaDurationMs(input)
        return parts.mapIndexed { index, part ->
            val committed = File(outputDir, part.name.replace(".part", ""))
            committed.delete()
            check(part.renameTo(committed)) { "无法提交音频分片 ${part.name}" }
            val start = index * segmentDurationMs
            AudioSegment(index, committed, start, minOf(segmentDurationMs, (totalDuration - start).coerceAtLeast(0L)))
        }
    }

    private fun mediaDurationMs(input: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(input.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    private fun muxRange(input: File, track: Int, format: MediaFormat, startUs: Long, endUs: Long, target: File) {
        val extractor = MediaExtractor().apply { setDataSource(input.absolutePath); selectTrack(track); seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC) }
        val muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val outputTrack = muxer.addTrack(format)
        val maximumSampleSize = runCatching { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }.getOrDefault(1024 * 1024)
        val buffer = ByteBuffer.allocate(maximumSampleSize.coerceAtLeast(1024 * 1024))
        val info = android.media.MediaCodec.BufferInfo()
        muxer.start()
        try {
            while (true) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0 || sampleTime >= endUs) break
                if (sampleTime < startUs) { extractor.advance(); continue }
                buffer.clear()
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = sampleTime - startUs
                @Suppress("WrongConstant")
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outputTrack, buffer, info)
                extractor.advance()
            }
        } finally {
            runCatching { muxer.stop() }
            muxer.release()
            extractor.release()
        }
    }

    private companion object { const val TAG = "AudioSegmenter" }
}
