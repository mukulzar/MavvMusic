package com.mavvmusic.core.data.stream

import android.util.Log
import com.mavvmusic.core.data.IoDispatcher
import com.mavvmusic.core.domain.ResolvedStream
import com.mavvmusic.core.domain.StreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class InnerTubeStreamResolver @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : StreamResolver {

    override suspend fun resolve(trackId: String): ResolvedStream = withContext(ioDispatcher) {
        require(trackId.isNotBlank()) { "A YouTube Music track ID is required." }
        val url = "https://music.youtube.com/watch?v=$trackId"

        try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, url)
            val bestAudio = info.audioStreams
                .filter { it.isUrl && !it.url.isNullOrBlank() }
                .maxByOrNull { it.averageBitrate }
                ?: throw IllegalStateException("No audio stream found for $trackId")

            val streamUrl = requireNotNull(bestAudio.url)
            Log.d(
                "MavvMusicPlay",
                "Resolved track $trackId with NewPipeExtractor using ${bestAudio.format?.mimeType}.",
            )
            ResolvedStream(
                uri = streamUrl,
                mimeType = bestAudio.format?.mimeType,
                bitrate = bestAudio.averageBitrate.takeIf { it >= 0 },
                expiresAtMs = System.currentTimeMillis() + STREAM_URL_TTL_MS,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("MavvMusicError", "NewPipeExtractor stream resolution failed for $trackId.", error)
            throw error
        }
    }

    private companion object {
        const val STREAM_URL_TTL_MS = 2 * 60 * 60 * 1000L
    }
}
