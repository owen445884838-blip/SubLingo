package com.sublingo.app.data.library

import com.sublingo.app.data.db.VideoDao
import com.sublingo.app.domain.model.PlayerRequest
import com.sublingo.app.domain.model.VideoSummary
import com.sublingo.app.domain.provider.LibraryProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomLibraryProvider @Inject constructor(
    private val videoDao: VideoDao,
) : LibraryProvider {
    override fun observeLibrary(): Flow<List<VideoSummary>> = videoDao.observeAll().map { videos ->
        videos.map { it.toSummary() }
    }

    override fun observeVideo(videoId: String): Flow<VideoSummary?> = videoDao.observeById(videoId).map { it?.toSummary() }

    override suspend fun openPlayer(request: PlayerRequest): String =
        "player://${request.videoId}?start=${request.startPositionMs}"

    private fun com.sublingo.app.data.db.VideoEntity.toSummary(): VideoSummary = VideoSummary(
        id = id,
        title = title ?: filePath?.substringAfterLast('/') ?: id,
        source = source ?: "Local",
        durationMs = durationMs,
        filePath = filePath,
        progressLabel = if (filePath.isNullOrBlank()) "下载中" else "已下载",
    )
}
