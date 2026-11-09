package com.mavvmusic.nativebridge

import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.ReactPackage
import com.facebook.react.uimanager.ViewManager
import dagger.hilt.android.EntryPointAccessors

class MavvMusicPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        val entryPoint = EntryPointAccessors.fromApplication(
            reactContext.applicationContext,
            MavvMusicBridgeEntryPoint::class.java,
        )
        return listOf(MavvMusicModule(reactContext, entryPoint.musicMviViewModel()))
    }

    override fun createViewManagers(
        reactContext: ReactApplicationContext,
    ): List<ViewManager<*, *>> = emptyList()
}
