package com.pranavkd.instadown.domain.usecase

import com.pranavkd.instadown.domain.model.MediaInfo
import com.pranavkd.instadown.domain.model.MediaTrackType
import com.pranavkd.instadown.domain.repository.DownloadRepository
import javax.inject.Inject

class StartDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(mediaInfo: MediaInfo): Result<Long> {
        return runCatching {
            downloadRepository.enqueue(mediaInfo)
        }
    }
}
