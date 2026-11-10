package com.mavvmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mavvmusic.app.presentation.SettingsIntent
import com.mavvmusic.app.presentation.SettingsViewModel
import com.mavvmusic.app.presentation.LibraryIntent
import com.mavvmusic.app.presentation.LibraryViewModel

@Composable
fun SettingsScreen(
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by settingsViewModel.state.collectAsStateWithLifecycle()
    var confirmClearCache by rememberSaveable { mutableStateOf(false) }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.cachedTrackCount, state.historyRevision) {
        libraryViewModel.onIntent(LibraryIntent.Refresh)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 12.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = "Settings",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        ListItem(
            headlineContent = { Text("Auto-cache new music") },
            supportingContent = {
                Text("Save tracks to this device as they play for offline listening.")
            },
            trailingContent = {
                if (state.isSaving) {
                    CircularProgressIndicator()
                } else {
                    Switch(
                        checked = state.autoCacheOnPlay,
                        onCheckedChange = {
                            settingsViewModel.onIntent(SettingsIntent.AutoCacheChanged(it))
                        },
                    )
                }
            },
        )

        ListItem(
            headlineContent = { Text("Offline music") },
            supportingContent = {
                Text(
                    "${state.cachedTrackCount} ${
                        if (state.cachedTrackCount == 1) "track" else "tracks"
                    } · ${formatBytes(state.cachedBytes)} used",
                )
            },
            trailingContent = {
                TextButton(
                    onClick = { confirmClearCache = true },
                    enabled = state.cachedTrackCount > 0 && !state.isClearingCache,
                ) {
                    if (state.isClearingCache) {
                        CircularProgressIndicator()
                    } else {
                        Text("Clear")
                    }
                }
            },
        )

        ListItem(
            headlineContent = { Text("Listening history") },
            supportingContent = { Text("Clear the tracks listed in your Library history.") },
            trailingContent = {
                TextButton(
                    onClick = { confirmClearHistory = true },
                    enabled = !state.isClearingHistory,
                ) {
                    if (state.isClearingHistory) {
                        CircularProgressIndicator()
                    } else {
                        Text("Clear")
                    }
                }
            },
        )

        state.error?.let { error ->
            Text(
                text = error,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (confirmClearCache) {
        AlertDialog(
            onDismissRequest = { confirmClearCache = false },
            title = { Text("Remove offline music?") },
            text = { Text("All downloaded audio will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.onIntent(SettingsIntent.ClearCachedMusic)
                        confirmClearCache = false
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearCache = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear listening history?") },
            text = { Text("Your liked songs, playlists, and downloads will not be affected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        settingsViewModel.onIntent(SettingsIntent.ClearListeningHistory)
                        confirmClearHistory = false
                    },
                ) { Text("Clear history") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
