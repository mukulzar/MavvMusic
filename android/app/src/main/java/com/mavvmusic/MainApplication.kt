package com.mavvmusic

import android.app.Application
import com.mavvmusic.core.data.network.OkHttpDownloader
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.mavvmusic.nativebridge.MavvMusicPackage
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application(), ReactApplication {
  @Inject
  lateinit var okHttpClient: OkHttpClient

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          // Packages that cannot be autolinked yet can be added manually here, for example:
          add(MavvMusicPackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    NewPipe.init(OkHttpDownloader(okHttpClient))
    loadReactNative(this)
  }
}
