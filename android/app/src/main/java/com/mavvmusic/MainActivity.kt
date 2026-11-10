package com.mavvmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mavvmusic.ui.MainScreen
import com.mavvmusic.ui.theme.MavvMusicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MavvMusicTheme {
                MainScreen()
            }
        }
    }
}
