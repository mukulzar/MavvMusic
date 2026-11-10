package com.mavvmusic.feature.cache

import android.content.Context
import android.net.Uri
import com.mavvmusic.core.database.CachedTrackEntity
import com.mavvmusic.core.database.MusicDao
import com.mavvmusic.core.database.toMetadataEntity
import com.mavvmusic.core.domain.CacheRepository
import com.mavvmusic.core.domain.ResolvedStream
import com.mavvmusic.core.domain.StreamHttpException
import com.mavvmusic.core.model.CachedTrack
import com.mavvmusic.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class AutoCacheRepository(
    private val context: Context,
    private val musicDao: MusicDao,
    private val httpClient: OkHttpClient,
    private val maxCacheBytes: Long = DEFAULT_CACHE_LIMIT_BYTES,
) : CacheRepository {
    private val cacheDirectory = File(context.filesDir, "music-cache")

    override suspend fun cache(track: Track, stream: ResolvedStream): CachedTrack =
        withContext(Dispatchers.IO) {
            musicDao.upsertTrackMetadata(track.toMetadataEntity())
            musicDao.findCachedTrack(track.id)?.let { existing ->
                val file = File(Uri.parse(existing.filePath).path.orEmpty())
                if (file.isFile) return@withContext existing.toCachedTrack()
            }

            if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
                throw IOException("Unable to create the music cache directory.")
            }
            val finalFile = File(cacheDirectory, "${track.id}.audio")
            val temporaryFile = File(cacheDirectory, "${track.id}.tmp")
            try {
                copyStreamToFile(stream, temporaryFile)
            } catch (error: Exception) {
                if (temporaryFile.exists() && !temporaryFile.delete()) {
                    error.addSuppressed(IOException("Unable to remove the incomplete cache file."))
                }
                throw error
            }
            val sizeBytes = temporaryFile.length()
            if (sizeBytes <= 0L) {
                temporaryFile.delete()
                throw IOException("The resolved audio source was empty.")
            }
            if (sizeBytes > maxCacheBytes) {
                temporaryFile.delete()
                throw IOException("The track exceeds the configured cache size limit.")
            }

            evictUntilFits(sizeBytes, track.id)
            if (finalFile.exists() && !finalFile.delete()) {
                temporaryFile.delete()
                throw IOException("Unable to replace the previous cached audio file.")
            }
            if (!temporaryFile.renameTo(finalFile)) {
                temporaryFile.delete()
                throw IOException("Unable to finalize the cached audio file.")
            }

            val now = System.currentTimeMillis()
            val entity = CachedTrackEntity(
                trackId = track.id,
                filePath = Uri.fromFile(finalFile).toString(),
                sizeBytes = sizeBytes,
                cachedAtMs = now,
                lastPlayedAtMs = now,
            )
            musicDao.upsertCachedTrack(entity)
            entity.toCachedTrack()
        }

    private fun copyStreamToFile(stream: ResolvedStream, destination: File) {
        val sourceUri = Uri.parse(stream.uri)
        if (sourceUri.scheme.equals("http", ignoreCase = true)
            || sourceUri.scheme.equals("https", ignoreCase = true)
        ) {
            val request = Request.Builder().url(stream.uri).build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code == HTTP_FORBIDDEN) {
                    throw StreamHttpException(
                        statusCode = response.code,
                        message = "The audio URL expired before it could be cached.",
                    )
                }
                if (!response.isSuccessful) {
                    throw IOException("The audio source returned HTTP ${response.code}.")
                }
                val body = response.body
                    ?: throw IOException("The audio source returned an empty response.")
                body.byteStream().use { source ->
                    destination.outputStream().use { target -> source.copyTo(target) }
                }
            }
        } else {
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("The resolved audio source could not be opened.")
            input.use { source ->
                destination.outputStream().use { target -> source.copyTo(target) }
            }
        }
    }

    override suspend fun getCachedTrack(trackId: String): CachedTrack? =
        withContext(Dispatchers.IO) {
            val entity = musicDao.findCachedTrack(trackId) ?: return@withContext null
            if (!File(Uri.parse(entity.filePath).path.orEmpty()).isFile) {
                musicDao.deleteCachedTrack(trackId)
                return@withContext null
            }
            entity.toCachedTrack()
        }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        musicDao.cachedTracksByLeastRecentlyPlayed().forEach { entry ->
            val file = File(Uri.parse(entry.filePath).path.orEmpty())
            if (file.exists() && !file.delete()) {
                throw IOException("Unable to remove cached audio for track ${entry.trackId}.")
            }
        }
        musicDao.clearCachedTracks()
    }

    override suspend fun cachedTrackIds(): Set<String> =
        withContext(Dispatchers.IO) { musicDao.cachedTrackIds().toSet() }

    private suspend fun evictUntilFits(requiredBytes: Long, replacingTrackId: String) {
        val entries = musicDao.cachedTracksByLeastRecentlyPlayed()
        var currentBytes = entries
            .filterNot { it.trackId == replacingTrackId }
            .sumOf { it.sizeBytes }
        for (entry in entries) {
            if (currentBytes + requiredBytes <= maxCacheBytes) break
            if (entry.trackId == replacingTrackId) continue
            val file = File(Uri.parse(entry.filePath).path.orEmpty())
            if (file.exists() && !file.delete()) {
                throw IOException("Unable to evict cached audio for track ${entry.trackId}.")
            }
            musicDao.deleteCachedTrack(entry.trackId)
            currentBytes -= entry.sizeBytes
        }
    }

    private fun CachedTrackEntity.toCachedTrack() = CachedTrack(
        trackId = trackId,
        filePath = filePath,
        sizeBytes = sizeBytes,
        cachedAtMs = cachedAtMs,
        lastPlayedAtMs = lastPlayedAtMs,
    )

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val DEFAULT_CACHE_LIMIT_BYTES = 256L * 1024L * 1024L
    }
}
