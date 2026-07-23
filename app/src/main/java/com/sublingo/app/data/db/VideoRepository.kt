package com.sublingo.app.data.db

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val dao: VideoDao,
) {
    fun observeAll(): Flow<List<VideoEntity>> = dao.observeAll()
    fun observeById(id: String): Flow<VideoEntity?> = dao.observeById(id)

    suspend fun upsert(entity: VideoEntity) = dao.upsert(entity)

    suspend fun replaceFilePath(videoId: String, filePath: String, fileSize: Long = 0L) {
        val current = dao.getById(videoId) ?: VideoEntity(id = videoId)
        dao.upsert(current.copy(filePath = filePath, fileSize = fileSize, updatedAt = System.currentTimeMillis()))
    }
}
