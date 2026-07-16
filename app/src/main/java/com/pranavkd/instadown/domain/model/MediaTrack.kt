package com.pranavkd.instadown.domain.model

data class MediaTrack(
    val id: String,
    val type: MediaTrackType,
    val quality: String?,
    val resolution: String?,
    val extension: String,
    val downloadUrl: String
)
