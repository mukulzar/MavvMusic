package com.mavvmusic.feature.player

import com.mavvmusic.core.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlaybackStateStore {
    private val mutableState = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    fun setCurrent(state: PlaybackState) {
        mutableState.value = state
    }

    fun setLoading(isLoading: Boolean) {
        mutableState.update { current ->
            current.copy(
                isLoading = isLoading,
                isPlaying = if (isLoading) false else current.isPlaying,
                error = if (isLoading) null else current.error,
            )
        }
    }

    fun updatePosition(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        mutableState.update { current ->
            current.copy(
                positionMs = positionMs.coerceAtLeast(0),
                durationMs = durationMs.coerceAtLeast(0),
                isPlaying = isPlaying,
                error = null,
            )
        }
    }

    fun reportError(message: String) {
        mutableState.update {
            it.copy(isPlaying = false, isLoading = false, error = message)
        }
    }
}
