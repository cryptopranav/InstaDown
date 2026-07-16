package com.pranavkd.instadown.data.remote.api

import com.pranavkd.instadown.data.remote.dto.MediaResolveResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface InstagramApiService {
    @GET("download")
    suspend fun resolveMedia(@Query("url") url: String): MediaResolveResponseDto
}
