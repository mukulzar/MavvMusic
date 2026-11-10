package com.mavvmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mavvmusic.app.presentation.PlayerIntent
import com.mavvmusic.app.presentation.PlayerViewModel

@Composable
fun MiniPlayer(
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by playerViewModel.state.collectAsStateWithLifecycle()
    val playback = state.playback
    val track = playback.currentTrack
    val isLoading = state.isLoading || playback.isLoading
    val progress = if (playback.durationMs > 0) {
        (playback.positionMs.toFloat() / playback.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier.size(44.dp).clip(MaterialTheme.shapes.small),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    if (track?.artworkUri != null) {
                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = "Album art for ${track.title}",
                            modifier = Modifier.size(44.dp),
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

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = track?.title ?: "Nothing playing",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Spacer(Modifier.size(2.dp))
                    Text(
                        text = state.error ?: track?.artist?.name ?: "Choose a track to begin",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.error != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                IconButton(
                    onClick = {
                        playerViewModel.onIntent(
                            if (playback.isPlaying) PlayerIntent.Pause else PlayerIntent.Resume,
                        )
                    },
                    enabled = track != null && !isLoading,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playback.isPlaying) "Pause" else "Play",
                        )
                    }
                }
                IconButton(
                    onClick = {
                        track?.let { playerViewModel.onIntent(PlayerIntent.StartRadio(it)) }
                    },
                    enabled = track != null && !isLoading,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Radio,
                        contentDescription = track?.let { "Start mix from ${it.title}" } ?: "Start mix",
                    )
                }
                IconButton(
                    onClick = { playerViewModel.onIntent(PlayerIntent.Next) },
                    enabled = track != null && !isLoading,
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next track",
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}
