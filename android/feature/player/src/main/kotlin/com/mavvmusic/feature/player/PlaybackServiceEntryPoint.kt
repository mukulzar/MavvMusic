package com.mavvmusic.feature.player

import com.mavvmusic.core.domain.StreamResolver
import androidx.media3.datasource.cache.Cache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackServiceEntryPoint {
    fun playbackStateStore(): PlaybackStateStore
    fun streamResolver(): StreamResolver
    fun mediaCache(): Cache
}
