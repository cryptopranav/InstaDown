package com.pranavkd.instadown.domain.repository

import com.pranavkd.instadown.domain.model.DownloadItem
import com.pranavkd.instadown.domain.model.MediaInfo
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeAll(): Flow<List<DownloadItem>>
    suspend fun enqueue(mediaInfo: MediaInfo): Long
    suspend fun pause(groupId: Long)
    suspend fun resume(groupId: Long)
    suspend fun delete(groupId: Long)
    suspend fun retry(groupId: Long)
}
