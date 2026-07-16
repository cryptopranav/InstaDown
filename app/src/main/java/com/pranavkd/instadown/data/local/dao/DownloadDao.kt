package com.pranavkd.instadown.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pranavkd.instadown.data.local.entity.DownloadGroupEntity
import com.pranavkd.instadown.data.local.entity.DownloadTrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_groups ORDER BY createdAt DESC")
    fun observeAllGroups(): Flow<List<DownloadGroupEntity>>

    @Query("SELECT * FROM download_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): DownloadGroupEntity?

    @Query("SELECT * FROM download_groups WHERE id = :id")
    fun observeGroupById(id: Long): Flow<DownloadGroupEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: DownloadGroupEntity): Long

    @Update
    suspend fun updateGroup(group: DownloadGroupEntity)

    @Delete
    suspend fun deleteGroup(group: DownloadGroupEntity)

    @Query("SELECT * FROM download_tracks WHERE groupId = :groupId")
    suspend fun getTracksForGroup(groupId: Long): List<DownloadTrackEntity>

    @Query("SELECT * FROM download_tracks WHERE groupId = :groupId")
    fun observeTracksForGroup(groupId: Long): Flow<List<DownloadTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: DownloadTrackEntity): Long

    @Update
    suspend fun updateTrack(track: DownloadTrackEntity)

    @Query("UPDATE download_tracks SET downloadedBytes = :bytes WHERE id = :id")
    suspend fun updateTrackProgress(id: Long, bytes: Long)

    @Delete
    suspend fun deleteTrack(track: DownloadTrackEntity)

    @Query("DELETE FROM download_tracks WHERE groupId = :groupId")
    suspend fun deleteTracksForGroup(groupId: Long)
}
