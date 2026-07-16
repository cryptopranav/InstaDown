package com.pranavkd.instadown.di

import com.pranavkd.instadown.data.repository.DownloadRepositoryImpl
import com.pranavkd.instadown.data.repository.MediaRepositoryImpl
import com.pranavkd.instadown.domain.repository.DownloadRepository
import com.pranavkd.instadown.domain.repository.MediaRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository
}
