package com.sublingo.app.domain.provider

import com.sublingo.app.domain.model.PlayerRequest
import com.sublingo.app.domain.model.VideoSummary
import kotlinx.coroutines.flow.Flow

interface LibraryProvider {
    fun observeLibrary(): Flow<List<VideoSummary>>
    fun observeVideo(videoId: String): Flow<VideoSummary?>
    suspend fun openPlayer(request: PlayerRequest): String
}
