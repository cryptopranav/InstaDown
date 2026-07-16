package com.pranavkd.instadown.domain.model

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, MUXING, COMPLETED, FAILED, CANCELLED
}
