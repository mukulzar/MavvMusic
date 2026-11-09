package com.mavvmusic.nativebridge

import android.util.Log
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.WritableMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.mavvmusic.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MavvMusicModule(
    reactContext: ReactApplicationContext,
    private val viewModel: MusicMviViewModel,
) : ReactContextBaseJavaModule(reactContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateJob: Job? = null
    private var effectJob: Job? = null

    override fun getName() = NAME

    override fun initialize() {
        super.initialize()
        stateJob = scope.launch {
            viewModel.state.collect { state ->
                emit(STATE_EVENT, Arguments.createMap().apply {
                    putString("stateJson", state.toJson().toString())
                })
            }
        }
        effectJob = scope.launch {
            viewModel.effects.collect { effect ->
                val payload = Arguments.createMap().apply {
                    when (effect) {
                        is MusicEffect.ShowError -> {
                            putString("type", "showError")
                            putString("message", effect.message)
                        }
                        MusicEffect.OpenPlayerSheet -> putString("type", "openPlayerSheet")
                    }
                }
                emit(EFFECT_EVENT, payload)
            }
        }
    }

    override fun invalidate() {
        stateJob?.cancel()
        effectJob?.cancel()
        scope.cancel()
        super.invalidate()
    }

    @ReactMethod
    fun getState(promise: Promise) {
        promise.resolve(viewModel.state.value.toJson().toString())
    }

    @ReactMethod
    fun dispatch(action: String, payload: ReadableMap) {
        Log.d("MavvMusicBridge", "KOTLIN RECEIVED ACTION: $action")
        if (action == "player.play") {
            Log.d(
                "MavvMusicPlay",
                "Bridge received play action for track: ${payload.getString("trackId") ?: "unknown"}",
            )
        }
        viewModel.dispatch(action, JSONObject(payload.toHashMap()).toString())
    }

    @ReactMethod
    fun addListener(eventName: String) = Unit

    @ReactMethod
    fun removeListeners(count: Double) = Unit

    private fun emit(eventName: String, payload: WritableMap) {
        if (!reactApplicationContext.hasActiveReactInstance()) return
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, payload)
    }

    companion object {
        const val NAME = "MavvMusic"
        const val STATE_EVENT = "mavvMusic.state"
        const val EFFECT_EVENT = "mavvMusic.effect"
    }
}

private fun MusicAppState.toJson() = JSONObject().apply {
    put("home", JSONObject().apply {
        put("tracks", JSONArray().apply { home.tracks.forEach { put(it.toJson()) } })
        put("isLoading", home.isLoading)
        put("activeFilter", home.activeFilter)
        put("libraryFilter", home.libraryFilter)
        put("likedTrackIds", JSONArray(home.likedTrackIds.toList()))
        put("error", home.error)
    })
    put("search", JSONObject().apply {
        put("query", search.query)
        put("results", JSONArray().apply { search.results.forEach { put(it.toJson()) } })
        put("isLoading", search.isLoading)
        put("error", search.error)
    })
    put("player", JSONObject().apply {
        put("currentTrack", player.playback.currentTrack?.toJson())
        put("isPlaying", player.playback.isPlaying)
        put("positionMs", player.playback.positionMs)
        put("durationMs", player.playback.durationMs)
        put("cacheStatus", player.playback.cacheStatus.name.lowercase())
        put("isLoading", player.isLoading)
        put("error", player.error)
    })
    put("cache", JSONObject().apply {
        put("cachedTrackIds", JSONArray(cache.cachedTrackIds.toList()))
        put("isClearing", cache.isClearing)
        put("error", cache.error)
    })
    put("settings", JSONObject().apply {
        put("autoCacheOnPlay", settings.autoCacheOnPlay)
        put("isSaving", settings.isSaving)
        put("error", settings.error)
    })
}

private fun Track.toJson() = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("artist", artist.name)
    put("artistId", artist.id)
    put("album", album.title)
    put("albumId", album.id)
    put("durationMs", durationMs)
    put("artworkUri", artworkUri)
}
