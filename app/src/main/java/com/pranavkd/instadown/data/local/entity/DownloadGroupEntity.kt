package com.pranavkd.instadown.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_groups")
data class DownloadGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val title: String?,
    val thumbnailUrl: String?,
    val outputFileName: String,
    val outputMimeType: String,
    val requiresMux: Boolean,
    val status: String,
    val mediaStoreUri: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
