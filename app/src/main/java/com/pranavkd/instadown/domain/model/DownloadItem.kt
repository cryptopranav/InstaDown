package com.pranavkd.instadown.domain.model

data class DownloadItem(
    val id: Long,
    val sourceUrl: String,
    val title: String?,
    val thumbnailUrl: String?,
    val outputFileName: String,
    val outputMimeType: String,
    val requiresMux: Boolean,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val mediaStoreUri: String?,
    val errorMessage: String?,
    val createdAt: Long
)
