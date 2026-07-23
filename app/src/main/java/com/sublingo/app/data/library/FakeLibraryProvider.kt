package com.sublingo.app.data.library

import com.sublingo.app.domain.model.PlayerRequest
import com.sublingo.app.domain.model.VideoSummary
import com.sublingo.app.domain.provider.LibraryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeLibraryProvider @Inject constructor() : LibraryProvider {
    private val items = MutableStateFlow(
        listOf(
            VideoSummary(
                id = "video-1001",
                title = "YouTube Sample Lesson",
                source = "YouTube",
                durationMs = 1_560_000,
                filePath = null,
                progressLabel = "下载中 30%",
            ),
            VideoSummary(
                id = "video-1002",
                title = "Bilibili Subtitle Demo",
                source = "Bilibili",
                durationMs = 2_100_000,
                filePath = "/storage/emulated/0/Movies/demo.mp4",
                progressLabel = "已就绪",
            ),
        )
    )

    override fun observeLibrary(): Flow<List<VideoSummary>> = items.asStateFlow()

    override fun observeVideo(videoId: String): Flow<VideoSummary?> = items.asStateFlow().map { list ->
        list.firstOrNull { it.id == videoId }
    }

    override suspend fun openPlayer(request: PlayerRequest): String =
        "player://${request.videoId}?start=${request.startPositionMs}"
}
