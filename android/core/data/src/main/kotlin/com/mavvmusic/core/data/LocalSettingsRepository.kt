package com.mavvmusic.core.data

import android.content.Context
import com.mavvmusic.core.domain.SettingsRepository
import com.mavvmusic.core.model.MusicSettings
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class LocalSettingsRepository(context: Context) : SettingsRepository {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val mutableSettings = MutableStateFlow(
        MusicSettings(
            autoCacheOnPlay = preferences.getBoolean(AUTO_CACHE_ON_PLAY, true),
        ),
    )

    override val settings: StateFlow<MusicSettings> = mutableSettings.asStateFlow()

    override suspend fun setAutoCacheOnPlay(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            val saved = preferences.edit().putBoolean(AUTO_CACHE_ON_PLAY, enabled).commit()
            if (!saved) throw IOException("The playback preference could not be saved.")
            mutableSettings.value = mutableSettings.value.copy(autoCacheOnPlay = enabled)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "mavv_music_settings"
        const val AUTO_CACHE_ON_PLAY = "auto_cache_on_play"
    }
}
