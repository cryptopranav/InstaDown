package com.pranavkd.instadown.data.mapper

import android.util.Log
import com.pranavkd.instadown.data.remote.dto.MediaAssetDto
import com.pranavkd.instadown.data.remote.dto.MediaResolveResponseDto
import com.pranavkd.instadown.domain.model.MediaInfo
import com.pranavkd.instadown.domain.model.MediaTrack
import com.pranavkd.instadown.domain.model.MediaTrackType

private const val TAG = "MediaMappers"

fun MediaResolveResponseDto.toDomain(sourceUrl: String): MediaInfo {
    return MediaInfo(
        sourceUrl = sourceUrl,
        author = author,
        title = title,
        thumbnailUrl = thumbnail,
        likeCount = likeCount,
        viewCount = viewCount,
        tracks = medias.map { it.toDomain() }
    )
}

fun MediaAssetDto.toDomain(): MediaTrack {
    return MediaTrack(
        id = id,
        type = type.toMediaTrackType(),
        quality = quality,
        resolution = resolution,
        extension = extension,
        downloadUrl = url
    )
}

fun String.toMediaTrackType(): MediaTrackType {
    return when (lowercase()) {
        "video" -> MediaTrackType.VIDEO
        "audio" -> MediaTrackType.AUDIO
        "image" -> MediaTrackType.IMAGE
        else -> {
            Log.w(TAG, "Unknown media track type: $this, falling back to IMAGE")
            MediaTrackType.IMAGE
        }
    }
}
