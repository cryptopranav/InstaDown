package com.pranavkd.instadown.domain.usecase

import com.pranavkd.instadown.domain.repository.DownloadRepository
import javax.inject.Inject

class ResumeDownloadUseCase @Inject constructor(
    private val downloadRepository: DownloadRepository
) {
    suspend operator fun invoke(groupId: Long) {
        downloadRepository.resume(groupId)
    }
}
