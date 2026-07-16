package com.pranavkd.instadown.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pranavkd.instadown.data.local.dao.DownloadDao
import com.pranavkd.instadown.data.local.entity.DownloadGroupEntity
import com.pranavkd.instadown.data.local.entity.DownloadTrackEntity

@Database(
    entities = [DownloadGroupEntity::class, DownloadTrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
}
