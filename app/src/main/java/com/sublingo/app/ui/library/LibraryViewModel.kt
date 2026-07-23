package com.sublingo.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sublingo.app.domain.model.PlayerRequest
import com.sublingo.app.domain.model.VideoSummary
import com.sublingo.app.domain.provider.LibraryProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val items: List<VideoSummary> = emptyList(),
    val status: String = "等待数据",
    val selectedPlayerRoute: String? = null,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryProvider: LibraryProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            libraryProvider.observeLibrary().collectLatest { items ->
                _uiState.value = _uiState.value.copy(
                    items = items,
                    status = if (items.isEmpty()) "暂无视频" else "已加载 ${items.size} 个视频",
                )
            }
        }
    }

    fun openPlayer(videoId: String) {
        viewModelScope.launch {
            val route = libraryProvider.openPlayer(PlayerRequest(videoId = videoId, startPositionMs = 0L))
            _uiState.value = _uiState.value.copy(selectedPlayerRoute = route)
        }
    }
}
