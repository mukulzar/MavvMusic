package com.mavvmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mavvmusic.app.presentation.HomeIntent
import com.mavvmusic.app.presentation.HomeViewModel
import com.mavvmusic.app.presentation.PlayerIntent
import com.mavvmusic.app.presentation.PlayerViewModel
import com.mavvmusic.core.model.Track

@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val state by homeViewModel.state.collectAsStateWithLifecycle()
    val errorMessage = state.error

    LaunchedEffect(homeViewModel) {
        homeViewModel.onIntent(HomeIntent.RefreshHomeFeed)
    }

    when {
        state.isLoading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        errorMessage != null -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
        state.tracks.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("No tracks are available yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(state.tracks, key = Track::id) { track ->
                TrackRow(track = track) {
                    playerViewModel.onIntent(PlayerIntent.Play(track, state.tracks))
                }
            }
        }
    }
}

@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.small),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
        ) {
            if (track.artworkUri != null) {
                AsyncImage(
                    model = track.artworkUri,
                    contentDescription = "Album art for ${track.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = track.artist.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
