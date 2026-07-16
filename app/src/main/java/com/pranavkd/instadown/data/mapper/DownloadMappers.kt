package com.pranavkd.instadown.data.mapper

import com.pranavkd.instadown.data.local.entity.DownloadGroupEntity
import com.pranavkd.instadown.data.local.entity.DownloadTrackEntity
import com.pranavkd.instadown.domain.model.DownloadItem
import com.pranavkd.instadown.domain.model.DownloadStatus
import com.pranavkd.instadown.domain.model.MediaInfo
import com.pranavkd.instadown.domain.model.MediaTrack
import com.pranavkd.instadown.domain.model.MediaTrackType

fun MediaInfo.toGroupEntity(outputFileName: String, outputMimeType: String, requiresMux: Boolean): DownloadGroupEntity {
    val now = System.currentTimeMillis()
    return DownloadGroupEntity(
        sourceUrl = sourceUrl,
        title = title,
        thumbnailUrl = thumbnailUrl,
        outputFileName = outputFileName,
        outputMimeType = outputMimeType,
        requiresMux = requiresMux,
        status = DownloadStatus.QUEUED.name,
        createdAt = now,
        updatedAt = now
    )
}

fun MediaTrack.toTrackEntity(groupId: Long, stagingFilePath: String): DownloadTrackEntity {
    return DownloadTrackEntity(
        groupId = groupId,
        trackType = type.name,
        remoteUrl = downloadUrl,
        stagingFilePath = stagingFilePath,
        status = DownloadStatus.QUEUED.name
    )
}

fun DownloadGroupEntity.toDownloadItem(tracks: List<DownloadTrackEntity>): DownloadItem {
    val totalBytes = tracks.map { it.totalBytes }.maxOrNull() ?: 0L
    val downloadedBytes = tracks.sumOf { it.downloadedBytes }
    val actualTotal = tracks.sumOf { it.totalBytes.coerceAtLeast(0) }

    return DownloadItem(
        id = id,
        sourceUrl = sourceUrl,
        title = title,
        thumbnailUrl = thumbnailUrl,
        outputFileName = outputFileName,
        outputMimeType = outputMimeType,
        requiresMux = requiresMux,
        status = deriveStatus(status, tracks),
        downloadedBytes = downloadedBytes,
        totalBytes = actualTotal.coerceAtLeast(downloadedBytes),
        mediaStoreUri = mediaStoreUri,
        errorMessage = errorMessage,
        createdAt = createdAt
    )
}

private fun deriveStatus(groupStatus: String, tracks: List<DownloadTrackEntity>): DownloadStatus {
    val raw = DownloadStatus.valueOf(groupStatus)
    if (raw != DownloadStatus.QUEUED && raw != DownloadStatus.DOWNLOADING) return raw

    val trackStatuses = tracks.map { DownloadStatus.valueOf(it.status) }
    return when {
        trackStatuses.all { it == DownloadStatus.COMPLETED } -> DownloadStatus.COMPLETED
        trackStatuses.any { it == DownloadStatus.FAILED } -> DownloadStatus.FAILED
        trackStatuses.any { it == DownloadStatus.DOWNLOADING } -> DownloadStatus.DOWNLOADING
        trackStatuses.any { it == DownloadStatus.PAUSED } -> DownloadStatus.PAUSED
        trackStatuses.any { it == DownloadStatus.MUXING } -> DownloadStatus.MUXING
        trackStatuses.any { it == DownloadStatus.QUEUED } -> DownloadStatus.QUEUED
        else -> raw
    }
}
