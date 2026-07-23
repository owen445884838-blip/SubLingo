package com.sublingo.app.data.repository

import com.sublingo.app.data.db.ProcessingJobDao
import com.sublingo.app.data.db.ProcessingJobEntity
import com.sublingo.app.data.db.VideoDao
import com.sublingo.app.data.db.VideoEntity
import com.sublingo.app.domain.repository.ProcessingJobRepository
import com.sublingo.app.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RoomVideoRepository @Inject constructor(
    private val videoDao: VideoDao,
) : VideoRepository {
    override fun observeVideos(): Flow<List<VideoEntity>> = videoDao.observeAll()

    override suspend fun upsertVideo(video: VideoEntity) {
        videoDao.upsert(video)
    }

    override suspend fun deleteVideos(ids: List<String>) {
        videoDao.deleteByIds(ids)
    }

    override suspend fun updatePlaybackPosition(id: String, positionMs: Long) {
        videoDao.updatePlaybackPosition(id, positionMs)
    }
}

class RoomProcessingJobRepository @Inject constructor(
    private val processingJobDao: ProcessingJobDao,
) : ProcessingJobRepository {
    override fun observeByVideoId(videoId: String): Flow<ProcessingJobEntity?> = processingJobDao.observeByVideoId(videoId)

    override fun observeJobs(): Flow<List<ProcessingJobEntity>> = processingJobDao.observeAll()

    override suspend fun upsertJob(job: ProcessingJobEntity) {
        processingJobDao.upsert(job)
    }
}
