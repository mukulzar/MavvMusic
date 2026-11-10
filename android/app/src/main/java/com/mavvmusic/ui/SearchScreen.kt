package com.mavvmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavvmusic.app.presentation.PlayerIntent
import com.mavvmusic.app.presentation.PlayerViewModel
import com.mavvmusic.app.presentation.SearchIntent
import com.mavvmusic.app.presentation.SearchViewModel
import com.mavvmusic.core.model.Track

@Composable
fun SearchScreen(
    playerViewModel: PlayerViewModel,
    searchViewModel: SearchViewModel = hiltViewModel(),
) {
    val state by searchViewModel.state.collectAsStateWithLifecycle()
    val errorMessage = state.error

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { searchViewModel.onIntent(SearchIntent.QueryChanged(it)) },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("Search music") },
            singleLine = true,
        )

        when {
            errorMessage != null -> Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
            )
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            state.query.isBlank() -> Text(
                text = "Search for a song, artist, or album.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.results.isEmpty() -> Text(
                text = "No results found.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(state.results, key = Track::id) { track ->
                    TrackRow(track = track) {
                        playerViewModel.onIntent(PlayerIntent.Play(track, state.results))
                    }
                }
            }
        }
    }
}
