package com.pranavkd.instadown.domain.usecase

import com.pranavkd.instadown.domain.model.DownloadItem
import com.pranavkd.instadown.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDownloadsUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadItem>> {
        return downloadRepository.observeAll()
    }
}
