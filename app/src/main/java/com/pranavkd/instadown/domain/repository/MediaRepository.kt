package com.pranavkd.instadown.domain.repository

import com.pranavkd.instadown.domain.model.MediaError
import com.pranavkd.instadown.domain.model.MediaInfo

interface MediaRepository {
    suspend fun resolve(url: String): Result<MediaInfo>
}
