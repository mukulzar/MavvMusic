package com.mavvmusic.di

import com.mavvmusic.core.data.repository.InnerTubeCatalogRepository
import com.mavvmusic.core.data.repository.RoomListeningHistoryRepository
import com.mavvmusic.core.domain.ListeningHistoryRepository
import com.mavvmusic.core.domain.MusicCatalogRepository
import com.mavvmusic.core.domain.StreamResolver
import com.mavvmusic.core.data.stream.InnerTubeStreamResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindListeningHistoryRepository(
        repository: RoomListeningHistoryRepository,
    ): ListeningHistoryRepository

    @Binds
    abstract fun bindMusicCatalogRepository(
        repository: InnerTubeCatalogRepository,
    ): MusicCatalogRepository

    @Binds
    abstract fun bindStreamResolver(
        resolver: InnerTubeStreamResolver,
    ): StreamResolver
}
