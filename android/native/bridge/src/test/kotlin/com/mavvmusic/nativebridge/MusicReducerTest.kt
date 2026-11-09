package com.mavvmusic.nativebridge

import com.mavvmusic.core.model.CacheStatus
import com.mavvmusic.core.model.MusicSettings
import com.mavvmusic.core.model.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicReducerTest {
    @Test
    fun searchQueryChangesToLoadingAndLoadedResultsClearLoading() {
        val loading = SearchReducer.reduce(SearchState(), SearchIntent.QueryChanged("blue"))

        assertEquals("blue", loading.query)
        assertTrue(loading.isLoading)

        val loaded = SearchReducer.reduce(loading, SearchIntent.ResultsLoaded(emptyList()))

        assertFalse(loaded.isLoading)
        assertTrue(loaded.error == null)
    }

    @Test
    fun playerPlaybackUpdateMirrorsNativePlaybackState() {
        val playing = PlaybackState(isPlaying = true, cacheStatus = CacheStatus.CACHED)

        val state = PlayerReducer.reduce(
            PlayerState(isLoading = true),
            PlayerIntent.PlaybackChanged(playing),
        )

        assertEquals(playing, state.playback)
        assertFalse(state.isLoading)
    }

    @Test
    fun playerPlaybackUpdatePreservesStreamResolutionLoadingState() {
        val loading = PlayerReducer.reduce(
            PlayerState(),
            PlayerIntent.PlaybackChanged(PlaybackState(isLoading = true)),
        )

        assertTrue(loading.isLoading)
        assertTrue(loading.playback.isLoading)

        val ready = PlayerReducer.reduce(
            loading,
            PlayerIntent.PlaybackChanged(PlaybackState(isPlaying = true)),
        )

        assertFalse(ready.isLoading)
        assertFalse(ready.playback.isLoading)
    }

    @Test
    fun playbackTickOnlyMatchesPositionDurationAndPlayingChanges() {
        val previous = PlayerState(playback = PlaybackState(positionMs = 100, durationMs = 900))
        val positionTick = previous.copy(
            playback = previous.playback.copy(positionMs = 600, isPlaying = true),
        )

        assertTrue(isPlaybackTickOnly(previous, positionTick))
        assertFalse(isPlaybackTickOnly(previous, positionTick.copy(isLoading = true)))
    }

    @Test
    fun playerFailureIsExposedOnPlaybackState() {
        val failed = PlayerReducer.reduce(
            PlayerState(isLoading = true),
            PlayerIntent.Failed("Stream is unavailable."),
        )

        assertEquals("Stream is unavailable.", failed.error)
        assertEquals("Stream is unavailable.", failed.playback.error)
        assertFalse(failed.isLoading)
    }

    @Test
    fun cacheClearTransitionsThroughClearingToEmpty() {
        val clearing = CacheReducer.reduce(
            CacheState(cachedTrackIds = setOf("track-1")),
            CacheIntent.Clear,
        )

        assertTrue(clearing.isClearing)
        val cleared = CacheReducer.reduce(clearing, CacheIntent.Cleared)
        assertTrue(cleared.cachedTrackIds.isEmpty())
        assertFalse(cleared.isClearing)
    }

    @Test
    fun homeReducerKeepsLikedTracksInNativeState() {
        val liked = HomeReducer.reduce(HomeState(), HomeIntent.TrackLiked("track-1"))
        assertTrue("track-1" in liked.likedTrackIds)

        val unliked = HomeReducer.reduce(liked, HomeIntent.TrackLiked("track-1"))
        assertFalse("track-1" in unliked.likedTrackIds)
    }

    @Test
    fun settingsReducerMirrorsPersistedAutoCachePreference() {
        val saving = SettingsReducer.reduce(
            SettingsState(),
            SettingsIntent.AutoCacheChanged(false),
        )
        assertFalse(saving.autoCacheOnPlay)
        assertTrue(saving.isSaving)

        val saved = SettingsReducer.reduce(
            saving,
            SettingsIntent.Loaded(MusicSettings(autoCacheOnPlay = false)),
        )
        assertFalse(saved.isSaving)
        assertFalse(saved.autoCacheOnPlay)
    }
}
