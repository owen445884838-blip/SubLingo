package com.sublingo.app.domain.repository

import com.sublingo.app.data.db.ProcessingJobEntity
import com.sublingo.app.data.db.VideoEntity
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun observeVideos(): Flow<List<VideoEntity>>
    suspend fun upsertVideo(video: VideoEntity)
    suspend fun deleteVideos(ids: List<String>)
    suspend fun updatePlaybackPosition(id: String, positionMs: Long)
}

interface ProcessingJobRepository {
    fun observeByVideoId(videoId: String): Flow<ProcessingJobEntity?>
    fun observeJobs(): Flow<List<ProcessingJobEntity>>
    suspend fun upsertJob(job: ProcessingJobEntity)
}
