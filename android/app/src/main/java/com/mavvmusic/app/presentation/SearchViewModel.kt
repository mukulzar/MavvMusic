package com.mavvmusic.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mavvmusic.core.domain.SearchTracksUseCase
import com.mavvmusic.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val results: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface SearchIntent {
    data class QueryChanged(val query: String) : SearchIntent
    data class ResultsLoaded(val results: List<Track>) : SearchIntent
    data class Failed(val message: String) : SearchIntent
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchTracks: SearchTracksUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchState())
    val state = mutableState.asStateFlow()
    private var searchJob: Job? = null

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                searchJob?.cancel()
                mutableState.value = SearchState(
                    query = intent.query,
                    isLoading = intent.query.isNotBlank(),
                )
                if (intent.query.isBlank()) return

                searchJob = viewModelScope.launch {
                    try {
                        val results = searchTracks(intent.query)
                        if (mutableState.value.query == intent.query) {
                            onIntent(SearchIntent.ResultsLoaded(results))
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (mutableState.value.query == intent.query) {
                            onIntent(
                                SearchIntent.Failed(
                                    error.message ?: "Search could not be completed.",
                                ),
                            )
                        }
                    }
                }
            }
            is SearchIntent.ResultsLoaded -> {
                mutableState.value = mutableState.value.copy(
                    results = intent.results,
                    isLoading = false,
                    error = null,
                )
            }
            is SearchIntent.Failed -> {
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = intent.message,
                )
            }
        }
    }
}
