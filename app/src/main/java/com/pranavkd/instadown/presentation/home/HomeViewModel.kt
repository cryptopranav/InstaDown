package com.pranavkd.instadown.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranavkd.instadown.domain.model.MediaError
import com.pranavkd.instadown.domain.model.MediaInfo
import com.pranavkd.instadown.domain.usecase.FetchMediaInfoUseCase
import com.pranavkd.instadown.domain.usecase.IsInstagramUrlUseCase
import com.pranavkd.instadown.domain.usecase.StartDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val isInstagramUrlUseCase: IsInstagramUrlUseCase,
    private val fetchMediaInfoUseCase: FetchMediaInfoUseCase,
    private val startDownloadUseCase: StartDownloadUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events = _events.asSharedFlow()

    fun fetchMedia(url: String) {
        if (!isInstagramUrlUseCase(url)) {
            _uiState.value = HomeUiState.Error("Invalid Instagram URL")
            return
        }
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            val result = fetchMediaInfoUseCase(url)
            result.fold(
                onSuccess = { mediaInfo ->
                    _uiState.value = HomeUiState.Preview(mediaInfo)
                },
                onFailure = { error ->
                    val message = when (error) {
                        is MediaError.NoNetwork -> "No internet connection"
                        is MediaError.Api -> error.message ?: "API error"
                        else -> error.message ?: "Something went wrong"
                    }
                    _uiState.value = HomeUiState.Error(message)
                }
            )
        }
    }

    fun startDownload(mediaInfo: MediaInfo) {
        viewModelScope.launch {
            val result = startDownloadUseCase(mediaInfo)
            result.fold(
                onSuccess = {
                    _events.emit(HomeEvent.DownloadStarted)
                    _uiState.value = HomeUiState.Idle
                },
                onFailure = {
                    _events.emit(HomeEvent.DownloadFailed("Failed to start download"))
                }
            )
        }
    }

    fun reset() {
        _uiState.value = HomeUiState.Idle
    }
}

sealed interface HomeEvent {
    data object DownloadStarted : HomeEvent
    data class DownloadFailed(val message: String) : HomeEvent
}
