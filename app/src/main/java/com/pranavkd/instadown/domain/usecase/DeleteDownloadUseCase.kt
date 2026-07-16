package com.pranavkd.instadown.domain.usecase

import com.pranavkd.instadown.domain.repository.DownloadRepository
import javax.inject.Inject

class DeleteDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(groupId: Long) {
        downloadRepository.delete(groupId)
    }
}
