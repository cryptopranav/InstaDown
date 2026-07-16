package com.pranavkd.instadown.di

import android.content.Context
import androidx.room.Room
import com.pranavkd.instadown.data.local.dao.DownloadDao
import com.pranavkd.instadown.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "instadown.db"
        ).build()
    }

    @Provides
    fun provideDownloadDao(database: AppDatabase): DownloadDao {
        return database.downloadDao()
    }
}
