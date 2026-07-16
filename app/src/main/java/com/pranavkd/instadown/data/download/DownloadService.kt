package com.pranavkd.instadown.data.download

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.app.ServiceCompat.STOP_FOREGROUND_REMOVE

import com.pranavkd.instadown.data.download.DownloadNotifier.Companion.ACTION_CANCEL
import com.pranavkd.instadown.data.download.DownloadNotifier.Companion.ACTION_PAUSE
import com.pranavkd.instadown.data.download.DownloadNotifier.Companion.EXTRA_GROUP_ID
import com.pranavkd.instadown.data.local.dao.DownloadDao
import com.pranavkd.instadown.data.local.entity.DownloadGroupEntity
import com.pranavkd.instadown.data.local.entity.DownloadTrackEntity
import com.pranavkd.instadown.domain.model.DownloadStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var downloadEngine: DownloadEngine
    @Inject lateinit var downloadNotifier: DownloadNotifier
    @Inject lateinit var mediaMuxer: MediaMuxer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<Long, MutableMap<Long, Job>>()

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
            if (groupId == -1L) return
            when (intent.action) {
                ACTION_PAUSE -> pause(groupId)
                ACTION_CANCEL -> delete(groupId)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            actionReceiver,
            IntentFilter().apply {
                addAction(ACTION_PAUSE)
                addAction(ACTION_CANCEL)
            },
            RECEIVER_NOT_EXPORTED
        )
        recoverStuckDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENQUEUE -> {
                val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
                if (groupId != -1L) enqueue(groupId)
            }
            ACTION_PAUSE -> {
                val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
                if (groupId != -1L) pause(groupId)
            }
            ACTION_RESUME -> {
                val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
                if (groupId != -1L) resume(groupId)
            }
            ACTION_DELETE -> {
                val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
                if (groupId != -1L) delete(groupId)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(actionReceiver)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun enqueue(groupId: Long) {
        serviceScope.launch {
            val group = downloadDao.getGroupById(groupId) ?: return@launch
            val tracks = downloadDao.getTracksForGroup(groupId)

            startForegroundService()
            downloadNotifier.showProgress(
                groupId, group.title ?: group.outputFileName, 0, "Starting…", true
            )

            val trackJobs = mutableMapOf<Long, Job>()
            tracks.forEach { track ->
                val job = launchTrackDownload(group, track)
                trackJobs[track.id] = job
            }
            activeJobs[groupId] = trackJobs

            trackJobs.values.forEach { it.join() }

            val updatedGroup = downloadDao.getGroupById(groupId) ?: return@launch
            val updatedTracks = downloadDao.getTracksForGroup(groupId)
            val allCompleted = updatedTracks.all {
                DownloadStatus.valueOf(it.status) == DownloadStatus.COMPLETED
            }
            if (!allCompleted) return@launch

            activeJobs.remove(groupId)

            if (group.requiresMux) {
                performMux(groupId, updatedGroup, updatedTracks)
            } else {
                finalizeSingleTrack(groupId, updatedGroup, updatedTracks)
            }
        }
    }

    private fun finalizeSingleTrack(groupId: Long, group: DownloadGroupEntity, tracks: List<DownloadTrackEntity>) {
        val track = tracks.firstOrNull() ?: return
        val stagingFile = File(track.stagingFilePath)
        if (!stagingFile.exists()) {
            serviceScope.launch {
                downloadDao.updateGroup(group.copy(
                    status = DownloadStatus.FAILED.name,
                    errorMessage = "Staging file missing",
                    updatedAt = System.currentTimeMillis()
                ))
            }
            return
        }
        serviceScope.launch {
            try {
                val uri = MediaStoreHelper.saveToMediaStore(
                    context = this@DownloadService,
                    file = stagingFile,
                    fileName = group.outputFileName,
                    mimeType = group.outputMimeType
                )
                stagingFile.delete()
                downloadDao.updateGroup(group.copy(
                    status = DownloadStatus.COMPLETED.name,
                    mediaStoreUri = uri?.toString(),
                    updatedAt = System.currentTimeMillis()
                ))
                downloadNotifier.showComplete(groupId, group.title ?: group.outputFileName)
            } catch (e: Exception) {
                downloadDao.updateGroup(group.copy(
                    status = DownloadStatus.FAILED.name,
                    errorMessage = "Save failed: ${e.message}",
                    updatedAt = System.currentTimeMillis()
                ))
                downloadNotifier.showFailed(
                    groupId,
                    group.title ?: group.outputFileName,
                    "Couldn't save file"
                )
            } finally {
                stopSelfIfIdle()
            }
        }
    }

    private fun launchTrackDownload(group: DownloadGroupEntity, track: DownloadTrackEntity): Job {
        return serviceScope.launch {
            try {
                downloadDao.updateTrack(track.copy(status = DownloadStatus.DOWNLOADING.name))
                downloadDao.updateGroup(group.copy(status = DownloadStatus.DOWNLOADING.name))

                val stagingFile = File(track.stagingFilePath)
                val startOffset = track.downloadedBytes

                val result = downloadEngine.download(
                    remoteUrl = track.remoteUrl,
                    stagingFile = stagingFile,
                    startOffset = startOffset,
                    onProgress = { progress ->
                        serviceScope.launch {
                            downloadDao.updateTrackProgress(track.id, progress.downloadedBytes)
                            updateNotification(group.id, tracksForGroup(group.id))
                        }
                    }
                )

                result.fold(
                    onSuccess = { bytes ->
                        if (bytes > 0) {
                            verifyTrackIntegrity(track.id, stagingFile)
                            downloadDao.updateTrack(
                                track.copy(
                                    status = DownloadStatus.COMPLETED.name,
                                    downloadedBytes = bytes
                                )
                            )
                        }
                    },
                    onFailure = { error ->
                        downloadDao.updateTrack(
                            track.copy(
                                status = DownloadStatus.FAILED.name,
                                downloadedBytes = stagingFile.length()
                            )
                        )
                        downloadDao.updateGroup(
                            group.copy(
                                status = DownloadStatus.FAILED.name,
                                errorMessage = error.message
                            )
                        )
                        downloadNotifier.showFailed(
                            group.id,
                            group.title ?: group.outputFileName,
                            error.message ?: "Download failed"
                        )
                    }
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    downloadDao.updateTrack(
                        track.copy(status = DownloadStatus.PAUSED.name)
                    )
                } else {
                    downloadDao.updateTrack(
                        track.copy(status = DownloadStatus.FAILED.name)
                    )
                }
            }
        }
    }

    private fun pause(groupId: Long) {
        activeJobs[groupId]?.values?.forEach { it.cancel() }
        activeJobs.remove(groupId)
        serviceScope.launch {
            val tracks = downloadDao.getTracksForGroup(groupId)
            tracks.forEach { track ->
                if (DownloadStatus.valueOf(track.status) == DownloadStatus.DOWNLOADING) {
                    downloadDao.updateTrack(track.copy(status = DownloadStatus.PAUSED.name))
                }
            }
            val group = downloadDao.getGroupById(groupId)
            if (group != null) {
                downloadDao.updateGroup(group.copy(status = DownloadStatus.PAUSED.name))
            }
            downloadNotifier.cancel(groupId)
        }
    }

    private fun resume(groupId: Long) {
        serviceScope.launch {
            val group = downloadDao.getGroupById(groupId) ?: return@launch
            val tracks = downloadDao.getTracksForGroup(groupId)
            val incompleteTracks = tracks.filter {
                val s = DownloadStatus.valueOf(it.status)
                s == DownloadStatus.PAUSED || s == DownloadStatus.FAILED || s == DownloadStatus.QUEUED
            }
            if (incompleteTracks.isEmpty()) return@launch

            downloadDao.updateGroup(group.copy(status = DownloadStatus.QUEUED.name))
            val trackJobs = mutableMapOf<Long, Job>()
            incompleteTracks.forEach { track ->
                val resetTrack = track.copy(status = DownloadStatus.QUEUED.name)
                downloadDao.updateTrack(resetTrack)
                val job = launchTrackDownload(group, resetTrack)
                trackJobs[track.id] = job
            }
            activeJobs[groupId] = trackJobs
        }
    }

    private fun delete(groupId: Long) {
        activeJobs[groupId]?.values?.forEach { it.cancel() }
        activeJobs.remove(groupId)
        downloadNotifier.cancel(groupId)
        serviceScope.launch {
            val tracks = downloadDao.getTracksForGroup(groupId)
            tracks.forEach { track ->
                File(track.stagingFilePath).delete()
            }
            downloadDao.getGroupById(groupId)?.let { group ->
                downloadDao.deleteGroup(group)
            }
        }
        stopSelfIfIdle()
    }

    private fun performMux(groupId: Long, group: DownloadGroupEntity, tracks: List<DownloadTrackEntity>) {
        serviceScope.launch {
            try {
                downloadDao.updateGroup(group.copy(status = DownloadStatus.MUXING.name))
                downloadNotifier.showFinalizing(groupId, group.title ?: group.outputFileName)

                val videoTrack = tracks.find { it.trackType == "VIDEO" }
                val audioTrack = tracks.find { it.trackType == "AUDIO" }

                if (videoTrack == null || audioTrack == null) {
                    throw IllegalStateException("Missing video or audio track for muxing")
                }

                val outputFile = File(applicationContext.filesDir, "muxed_${groupId}.mp4")
                val result = mediaMuxer.muxVideoAndAudio(
                    videoFile = File(videoTrack.stagingFilePath),
                    audioFile = File(audioTrack.stagingFilePath),
                    outputFile = outputFile
                )

                result.fold(
                    onSuccess = {
                        // Copy to MediaStore
                        val uri = MediaStoreHelper.saveToMediaStore(
                            context = this@DownloadService,
                            file = outputFile,
                            fileName = group.outputFileName,
                            mimeType = group.outputMimeType
                        )

                        File(videoTrack.stagingFilePath).delete()
                        File(audioTrack.stagingFilePath).delete()
                        outputFile.delete()

                        downloadDao.updateGroup(group.copy(
                            status = DownloadStatus.COMPLETED.name,
                            mediaStoreUri = uri?.toString(),
                            updatedAt = System.currentTimeMillis()
                        ))
                        downloadNotifier.showComplete(groupId, group.title ?: group.outputFileName)
                    },
                    onFailure = { error ->
                        downloadDao.updateGroup(group.copy(
                            status = DownloadStatus.FAILED.name,
                            errorMessage = "Muxing failed: ${error.message}",
                            updatedAt = System.currentTimeMillis()
                        ))
                        downloadNotifier.showFailed(
                            groupId,
                            group.title ?: group.outputFileName,
                            "Couldn't finalize video"
                        )
                    }
                )
            } catch (e: Exception) {
                downloadDao.updateGroup(group.copy(
                    status = DownloadStatus.FAILED.name,
                    errorMessage = "Muxing failed: ${e.message}"
                )                )
            } finally {
                stopSelfIfIdle()
            }
        }
    }

    private fun verifyTrackIntegrity(trackId: Long, file: File) {
        // Integrity check — currently a pass-through; actual size comparison
        // happens during the download flow via Content-Length.
    }

    private fun recoverStuckDownloads() {
        serviceScope.launch {
            val groups = downloadDao.observeAllGroups().first()
            groups.forEach { group ->
                val status = DownloadStatus.valueOf(group.status)
                if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.MUXING) {
                    val tracks = downloadDao.getTracksForGroup(group.id)
                    tracks.forEach { track ->
                        val ts = DownloadStatus.valueOf(track.status)
                        if (ts == DownloadStatus.DOWNLOADING) {
                            downloadDao.updateTrack(track.copy(status = DownloadStatus.PAUSED.name))
                        }
                    }
                    downloadDao.updateGroup(group.copy(
                        status = DownloadStatus.PAUSED.name,
                        updatedAt = System.currentTimeMillis()
                    ))
                }
            }
        }
    }

    private suspend fun tracksForGroup(groupId: Long): List<DownloadTrackEntity> {
        return try {
            downloadDao.getTracksForGroup(groupId)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun updateNotification(groupId: Long, tracks: List<DownloadTrackEntity>) {
        if (tracks.isEmpty()) return
        val group = try {
            downloadDao.getGroupById(groupId)
        } catch (_: Exception) {
            null
        } ?: return
        val totalBytes = tracks.sumOf { it.totalBytes.coerceAtLeast(0) }
        val downloadedBytes = tracks.sumOf { it.downloadedBytes }
        val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
        downloadNotifier.showProgress(
            groupId,
            group.title ?: group.outputFileName,
            progress,
            formatSpeed(downloadedBytes)
        )
    }

    private fun formatSpeed(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    private fun startForegroundService() {
        val notification = android.app.Notification.Builder(this, DownloadNotifier.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading…")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            @Suppress("DEPRECATION")
            startForeground(1, notification)
        }
    }

    private fun stopSelfIfIdle() {
        if (activeJobs.isEmpty()) {
            ServiceCompat.stopForeground(this, STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    companion object {
        const val ACTION_ENQUEUE = "com.pranavkd.instadown.action.ENQUEUE"
        const val ACTION_RESUME = "com.pranavkd.instadown.action.RESUME"
        const val ACTION_DELETE = "com.pranavkd.instadown.action.DELETE"

        fun enqueue(context: Context, groupId: Long) {
            context.startService(Intent(context, DownloadService::class.java).apply {
                action = ACTION_ENQUEUE
                putExtra(EXTRA_GROUP_ID, groupId)
            })
        }

        fun pause(context: Context, groupId: Long) {
            context.startService(Intent(context, DownloadService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_GROUP_ID, groupId)
            })
        }

        fun resume(context: Context, groupId: Long) {
            context.startService(Intent(context, DownloadService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_GROUP_ID, groupId)
            })
        }

        fun delete(context: Context, groupId: Long) {
            context.startService(Intent(context, DownloadService::class.java).apply {
                action = ACTION_DELETE
                putExtra(EXTRA_GROUP_ID, groupId)
            })
        }
    }
}
