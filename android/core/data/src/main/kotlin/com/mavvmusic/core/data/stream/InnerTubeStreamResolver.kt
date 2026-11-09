package com.mavvmusic.core.data.stream

import android.util.Log
import com.mavvmusic.core.domain.ResolvedStream
import com.mavvmusic.core.domain.StreamResolver
import com.mavvmusic.core.data.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
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
        val primaryUrl = "https://www.youtube.com/watch?v=$trackId"
        try {
            extractStream(primaryUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            Log.w("MavvMusicError", "Primary stream extraction failed; trying youtu.be fallback.", e)
            try {
                extractStream("https://youtu.be/$trackId")
            } catch (fallbackError: CancellationException) {
                throw fallbackError
            } catch (fallbackError: Exception) {
                fallbackError.addSuppressed(e)
                Log.e("MavvMusicError", "Stream resolve failed for $trackId.", fallbackError)
                throw fallbackError
            }
        }
    }

    private fun extractStream(url: String): ResolvedStream {
        val extractor = ServiceList.YouTube.getStreamExtractor(url)
        return try {
            val info = StreamInfo.getInfo(extractor)
            val bestAudio = info.audioStreams
                .filter { it.url != null }
                .maxByOrNull { it.averageBitrate }
                ?: throw IllegalStateException("No audio stream found for $url")

            ResolvedStream(
                uri = requireNotNull(bestAudio.url),
                expiresAtMs = System.currentTimeMillis() + (2 * 60 * 60 * 1000),
                mimeType = bestAudio.format?.mimeType,
                bitrate = bestAudio.averageBitrate,
            )
        } catch (error: Exception) {
            Log.e(
                "MavvMusicError",
                "Stream extraction failed for $url; NewPipe details: ${extractor.errorMessage}",
                error,
            )
            throw error
        }
    }
}