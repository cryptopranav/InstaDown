package com.pranavkd.instadown.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranavkd.instadown.domain.usecase.DeleteDownloadUseCase
import com.pranavkd.instadown.domain.usecase.ObserveDownloadsUseCase
import com.pranavkd.instadown.domain.usecase.PauseDownloadUseCase
import com.pranavkd.instadown.domain.usecase.ResumeDownloadUseCase
import com.pranavkd.instadown.domain.usecase.RetryDownloadUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    observeDownloadsUseCase: ObserveDownloadsUseCase,
    private val pauseDownloadUseCase: PauseDownloadUseCase,
    private val resumeDownloadUseCase: ResumeDownloadUseCase,
    private val deleteDownloadUseCase: DeleteDownloadUseCase,
    private val retryDownloadUseCase: RetryDownloadUseCase
) : ViewModel() {

    val downloads: StateFlow<List<com.pranavkd.instadown.domain.model.DownloadItem>> =
        observeDownloadsUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun pause(groupId: Long) {
        viewModelScope.launch { pauseDownloadUseCase(groupId) }
    }

    fun resume(groupId: Long) {
        viewModelScope.launch { resumeDownloadUseCase(groupId) }
    }

    fun delete(groupId: Long) {
        viewModelScope.launch { deleteDownloadUseCase(groupId) }
    }

    fun retry(groupId: Long) {
        viewModelScope.launch { retryDownloadUseCase(groupId) }
    }
}
