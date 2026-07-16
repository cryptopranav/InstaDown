package com.pranavkd.instadown.presentation.home

import com.pranavkd.instadown.domain.model.MediaInfo

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Loading : HomeUiState
    data class Preview(val mediaInfo: MediaInfo) : HomeUiState
    data class Error(val message: String) : HomeUiState
}
