package com.pranavkd.instadown.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "download_tracks",
    foreignKeys = [ForeignKey(
        entity = DownloadGroupEntity::class,
        parentColumns = ["id"],
        childColumns = ["groupId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class DownloadTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val trackType: String,
    val remoteUrl: String,
    val stagingFilePath: String,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val status: String
)
