package com.mavvmusic.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mavvmusic.core.domain.GetHomeFeedUseCase
import com.mavvmusic.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

sealed interface HomeIntent {
    data object RefreshHomeFeed : HomeIntent
    data class FeedLoaded(val tracks: List<Track>) : HomeIntent
    data class Failed(val message: String) : HomeIntent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getHomeFeed: GetHomeFeedUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    val state = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.RefreshHomeFeed -> {
                refreshJob?.cancel()
                mutableState.value = mutableState.value.copy(isLoading = true, error = null)
                refreshJob = viewModelScope.launch {
                    try {
                        onIntent(HomeIntent.FeedLoaded(getHomeFeed()))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        onIntent(HomeIntent.Failed(error.message ?: "Your music could not be loaded."))
                    }
                }
            }
            is HomeIntent.FeedLoaded -> {
                mutableState.value = HomeState(tracks = intent.tracks, isLoading = false)
            }
            is HomeIntent.Failed -> {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = intent.message,
                )
            }
        }
    }
}
