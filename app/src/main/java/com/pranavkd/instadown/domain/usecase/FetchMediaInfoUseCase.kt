package com.pranavkd.instadown.domain.usecase

import com.pranavkd.instadown.domain.model.MediaInfo
import com.pranavkd.instadown.domain.repository.MediaRepository
import javax.inject.Inject

class FetchMediaInfoUseCase @Inject constructor(
    private val mediaRepository: MediaRepository
) {
    suspend operator fun invoke(url: String): Result<MediaInfo> {
        return mediaRepository.resolve(url)
    }
}
