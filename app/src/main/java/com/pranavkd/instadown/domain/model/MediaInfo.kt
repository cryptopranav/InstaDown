package com.pranavkd.instadown.domain.model

data class MediaInfo(
    val sourceUrl: String,
    val author: String?,
    val title: String?,
    val thumbnailUrl: String?,
    val likeCount: Long?,
    val viewCount: Long?,
    val tracks: List<MediaTrack>
)
