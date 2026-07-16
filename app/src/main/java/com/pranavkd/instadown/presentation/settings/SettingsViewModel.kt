package com.pranavkd.instadown.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pranavkd.instadown.domain.model.DownloadItem
import com.pranavkd.instadown.domain.usecase.DeleteDownloadUseCase
import com.pranavkd.instadown.domain.usecase.ObserveDownloadsUseCase
import com.pranavkd.instadown.presentation.theme.ThemeManager
import com.pranavkd.instadown.presentation.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeDownloadsUseCase: ObserveDownloadsUseCase,
    private val deleteDownloadUseCase: DeleteDownloadUseCase,
    private val themeManager: ThemeManager,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _clearState = MutableStateFlow<ClearState>(ClearState.Idle)
    val clearState = _clearState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    val themeMode: StateFlow<ThemeMode> = themeManager.themeMode

    val downloads: StateFlow<List<DownloadItem>> =
        observeDownloadsUseCase()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appVersion: String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "1.0"
    }.getOrDefault("1.0")

    fun setThemeMode(mode: ThemeMode) {
        themeManager.setThemeMode(mode)
    }

    fun clearAllDownloads(downloads: List<DownloadItem>) {
        viewModelScope.launch {
            _clearState.value = ClearState.Clearing
            downloads.forEach { item ->
                deleteDownloadUseCase(item.id)
            }
            _clearState.value = ClearState.Idle
            _events.emit(SettingsEvent.DownloadsCleared)
        }
    }
}

sealed interface ClearState {
    data object Idle : ClearState
    data object Clearing : ClearState
}

sealed interface SettingsEvent {
    data object DownloadsCleared : SettingsEvent
}
