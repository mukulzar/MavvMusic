package com.mavvmusic.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mavvmusic.core.database.MusicDatabase
import com.mavvmusic.core.domain.ClearCacheUseCase
import com.mavvmusic.core.domain.ListeningHistoryRepository
import com.mavvmusic.core.domain.ObserveMusicSettingsUseCase
import com.mavvmusic.core.domain.SetAutoCacheOnPlayUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val autoCacheOnPlay: Boolean = true,
    val isSaving: Boolean = false,
    val cachedTrackCount: Int = 0,
    val cachedBytes: Long = 0L,
    val isClearingCache: Boolean = false,
    val isClearingHistory: Boolean = false,
    val historyRevision: Int = 0,
    val error: String? = null,
)

sealed interface SettingsIntent {
    data object Refresh : SettingsIntent
    data class AutoCacheChanged(val enabled: Boolean) : SettingsIntent
    data object ClearCachedMusic : SettingsIntent
    data object ClearListeningHistory : SettingsIntent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeSettings: ObserveMusicSettingsUseCase,
    private val setAutoCacheOnPlay: SetAutoCacheOnPlayUseCase,
    private val clearCache: ClearCacheUseCase,
    private val database: MusicDatabase,
    private val listeningHistory: ListeningHistoryRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SettingsState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            observeSettings().collect { settings ->
                mutableState.value = mutableState.value.copy(
                    autoCacheOnPlay = settings.autoCacheOnPlay,
                    isSaving = false,
                )
            }
        }
        onIntent(SettingsIntent.Refresh)
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.Refresh -> refresh()
            is SettingsIntent.AutoCacheChanged -> {
                mutableState.value = mutableState.value.copy(
                    autoCacheOnPlay = intent.enabled,
                    isSaving = true,
                    error = null,
                )
                viewModelScope.launch {
                    try {
                        setAutoCacheOnPlay(intent.enabled)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        mutableState.value = mutableState.value.copy(
                            autoCacheOnPlay = observeSettings().value.autoCacheOnPlay,
                            isSaving = false,
                            error = error.message ?: "The playback preference could not be saved.",
                        )
                    }
                }
            }
            SettingsIntent.ClearCachedMusic -> viewModelScope.launch {
                mutableState.value = mutableState.value.copy(isClearingCache = true, error = null)
                try {
                    clearCache()
                    refreshCacheStats()
                    mutableState.value = mutableState.value.copy(isClearingCache = false)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutableState.value = mutableState.value.copy(
                        isClearingCache = false,
                        error = error.message ?: "Downloaded music could not be removed.",
                    )
                }
            }
            SettingsIntent.ClearListeningHistory -> viewModelScope.launch {
                mutableState.value = mutableState.value.copy(isClearingHistory = true, error = null)
                try {
                    listeningHistory.clearHistory()
                    mutableState.value = mutableState.value.copy(
                        isClearingHistory = false,
                        historyRevision = mutableState.value.historyRevision + 1,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutableState.value = mutableState.value.copy(
                        isClearingHistory = false,
                        error = error.message ?: "Listening history could not be cleared.",
                    )
                }
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                refreshCacheStats()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = mutableState.value.copy(
                    error = error.message ?: "Storage information could not be loaded.",
                )
            }
        }
    }

    private suspend fun refreshCacheStats() {
        val cached = database.musicDao().cachedTracksByLeastRecentlyPlayed()
        mutableState.value = mutableState.value.copy(
            cachedTrackCount = cached.size,
            cachedBytes = cached.sumOf { it.sizeBytes },
        )
    }
}
