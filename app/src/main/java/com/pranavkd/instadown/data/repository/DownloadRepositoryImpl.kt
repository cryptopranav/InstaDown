package com.pranavkd.instadown.data.repository

import android.content.Context
import com.pranavkd.instadown.data.download.DownloadService
import com.pranavkd.instadown.data.local.dao.DownloadDao
import com.pranavkd.instadown.data.mapper.toDownloadItem
import com.pranavkd.instadown.data.mapper.toGroupEntity
import com.pranavkd.instadown.data.mapper.toTrackEntity
import com.pranavkd.instadown.domain.model.DownloadItem
import com.pranavkd.instadown.domain.model.DownloadStatus
import com.pranavkd.instadown.domain.model.MediaInfo
import com.pranavkd.instadown.domain.model.MediaTrackType
import com.pranavkd.instadown.domain.repository.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao,
    @param:ApplicationContext private val context: Context
) : DownloadRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeAll(): Flow<List<DownloadItem>> {
        return downloadDao.observeAllGroups().flatMapLatest { groups ->
            if (groups.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(groups.map { group ->
                    downloadDao.observeTracksForGroup(group.id)
                        .map { tracks -> group.toDownloadItem(tracks) }
                }) { items -> items.toList() }
            }
        }
    }

    override suspend fun enqueue(mediaInfo: MediaInfo): Long {
        val hasVideo = mediaInfo.tracks.any { it.type == MediaTrackType.VIDEO }
        val hasAudio = mediaInfo.tracks.any { it.type == MediaTrackType.AUDIO }
        val requiresMux = hasVideo && hasAudio
        val isVideo = hasVideo || hasAudio
        val outputMimeType = if (isVideo) "video/mp4" else "image/jpeg"
        val extension = if (isVideo) "mp4" else "jpg"
        val outputFileName = "instadown_${System.currentTimeMillis()}.$extension"

        val groupEntity = mediaInfo.toGroupEntity(
            outputFileName = outputFileName,
            outputMimeType = outputMimeType,
            requiresMux = requiresMux
        )
        val groupId = downloadDao.insertGroup(groupEntity)

        val appStoragePath = context.filesDir.absolutePath
        mediaInfo.tracks.forEach { track ->
            val ext = track.extension
            val stagingPath = "${appStoragePath}/${groupId}_${track.type.name.lowercase()}.$ext"
            val trackEntity = track.toTrackEntity(
                groupId = groupId,
                stagingFilePath = stagingPath
            )
            downloadDao.insertTrack(trackEntity)
        }

        DownloadService.enqueue(context, groupId)
        return groupId
    }

    override suspend fun pause(groupId: Long) {
        DownloadService.pause(context, groupId)
    }

    override suspend fun resume(groupId: Long) {
        DownloadService.resume(context, groupId)
    }

    override suspend fun delete(groupId: Long) {
        DownloadService.delete(context, groupId)
    }

    override suspend fun retry(groupId: Long) {
        val group = downloadDao.getGroupById(groupId) ?: return
        val tracks = downloadDao.getTracksForGroup(groupId)
        tracks.forEach { track ->
            downloadDao.updateTrack(track.copy(
                status = DownloadStatus.QUEUED.name,
                downloadedBytes = 0
            ))
            File(track.stagingFilePath).delete()
        }
        downloadDao.updateGroup(group.copy(
            status = DownloadStatus.QUEUED.name,
            errorMessage = null
        ))
        DownloadService.enqueue(context, groupId)
    }
}
