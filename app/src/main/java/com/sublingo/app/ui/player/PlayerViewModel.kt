package com.sublingo.app.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sublingo.app.domain.repository.VideoRepository
import com.sublingo.app.data.db.SubtitleCueDao
import com.sublingo.app.data.db.SubtitleCueEntity
import com.sublingo.app.data.db.SubtitleTrackDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.io.File
import javax.inject.Inject

data class PlayerUiState(
    val videoId: String = "",
    val startPositionMs: Long = 0L,
    val title: String = "本地视频",
    val filePath: String? = null,
    val companionAudioPath: String? = null,
    val durationMs: Long = 0L,
    val englishCues: List<SubtitleCueEntity> = emptyList(),
    val chineseCues: List<SubtitleCueEntity> = emptyList(),
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    subtitleTrackDao: SubtitleTrackDao,
    subtitleCueDao: SubtitleCueDao,
) : ViewModel() {
    private val videoId: String = savedStateHandle["videoId"] ?: ""
    private val startPositionMs: Long = savedStateHandle["startPositionMs"] ?: 0L

    private val subtitleState = subtitleTrackDao.observeByVideoId(videoId).flatMapLatest { tracks ->
        val english = tracks.firstOrNull { it.language.startsWith("en", true) && it.kind == "ASR" }
            ?: tracks.firstOrNull { it.language.startsWith("en", true) }
        val chinese = tracks.firstOrNull { it.language.startsWith("zh", true) }
        combine(
            english?.let { subtitleCueDao.observeByTrackId(it.id) } ?: flowOf(emptyList()),
            chinese?.let { subtitleCueDao.observeByTrackId(it.id) } ?: flowOf(emptyList()),
        ) { en, zh -> en to zh }
    }

    val uiState: StateFlow<PlayerUiState> = combine(videoRepository.observeVideos(), subtitleState) { videos, subtitles ->
            val video = videos.firstOrNull { it.id == videoId }
            val videoFile = video?.filePath?.let(::File)
            val companionAudio = videoFile?.parentFile?.listFiles().orEmpty()
                .filter { file: File ->
                    file.isFile && file.absolutePath != videoFile?.absolutePath &&
                        file.extension.lowercase() in setOf("m4a", "aac", "mp3", "opus", "ogg") &&
                        file.length() > 0L
                }
                .maxByOrNull { file: File -> file.length() }
            PlayerUiState(
                videoId = videoId,
                startPositionMs = if (startPositionMs > 0) startPositionMs else video?.lastPlayedPositionMs ?: 0L,
                title = video?.title ?: "本地视频",
                filePath = video?.filePath,
                companionAudioPath = companionAudio?.absolutePath,
                durationMs = video?.durationMs ?: 0L,
                englishCues = subtitles.first,
                chineseCues = subtitles.second,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerUiState(videoId = videoId, startPositionMs = startPositionMs),
        )

    fun savePosition(positionMs: Long) {
        if (videoId.isBlank()) return
        viewModelScope.launch { videoRepository.updatePlaybackPosition(videoId, positionMs.coerceAtLeast(0L)) }
    }
}
