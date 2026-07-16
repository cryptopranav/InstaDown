package com.pranavkd.instadown.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaResolveResponseDto(
    val status: Boolean,
    val author: String? = null,
    @SerialName("view_count") val viewCount: Long? = null,
    @SerialName("like_count") val likeCount: Long? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val medias: List<MediaAssetDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class MediaAssetDto(
    val id: String,
    val type: String,
    val quality: String? = null,
    val resolution: String? = null,
    val extension: String,
    val url: String
)
