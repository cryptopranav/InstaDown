package com.pranavkd.instadown.data.repository

import com.pranavkd.instadown.data.mapper.toDomain
import com.pranavkd.instadown.data.remote.api.InstagramApiService
import com.pranavkd.instadown.domain.model.MediaError
import com.pranavkd.instadown.domain.model.MediaInfo
import com.pranavkd.instadown.domain.repository.MediaRepository
import java.io.IOException
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
    private val apiService: InstagramApiService
) : MediaRepository {

    override suspend fun resolve(url: String): Result<MediaInfo> {
        return runCatching {
            val response = apiService.resolveMedia(url)
            if (!response.status) {
                throw MediaResolveException(response.error ?: "Unknown error")
            }
            response.toDomain(url)
        }
    }

    private class MediaResolveException(message: String) : Exception(message)
}

private fun <T> Result<T>.mapError(transform: (Throwable) -> MediaError): Result<T> {
    return fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(transform(it)) }
    )
}
