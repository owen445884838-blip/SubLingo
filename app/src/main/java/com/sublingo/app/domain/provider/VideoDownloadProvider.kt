package com.sublingo.app.domain.provider

import com.sublingo.app.data.db.VideoEntity
import com.sublingo.app.domain.model.DownloadRequest

interface VideoDownloadProvider {
    suspend fun download(request: DownloadRequest): String
    suspend fun toVideoEntity(request: DownloadRequest, filePath: String): VideoEntity
}
