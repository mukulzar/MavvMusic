package com.mavvmusic

import android.app.Application
import com.mavvmusic.core.data.network.NewPipeInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application() {
  @Inject lateinit var newPipeInitializer: NewPipeInitializer

  override fun onCreate() {
    super.onCreate()
    newPipeInitializer.initialize()
  }
}
