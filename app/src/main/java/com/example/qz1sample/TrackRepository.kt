package com.example.qz1sample

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

interface TrackRepository {
    suspend fun load(fileName: String): TrackRepositoryResult
}

sealed interface TrackRepositoryResult {
    data class Loaded(
        val track: Track,
        val phoneTrack: PhoneTrack?
    ) : TrackRepositoryResult
    data object NotFound : TrackRepositoryResult
    data object ReadFailed : TrackRepositoryResult
}

class FileTrackRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TrackRepository {
    override suspend fun load(fileName: String): TrackRepositoryResult = withContext(ioDispatcher) {
        try {
            val files = RecordingSessionFileStore.find(context, fileName)
                ?: return@withContext TrackRepositoryResult.NotFound
            val track = NmeaLogStore.readLines(context, fileName) { lines ->
                TrackBuilder.build(fileName, lines)
            }
            if (track == null) {
                TrackRepositoryResult.NotFound
            } else {
                val phoneTrack = when (files.phoneLocation) {
                    PhoneLocationSidecar.None -> null
                    is PhoneLocationSidecar.Available -> {
                        PhoneLocationLogStore.read(context, files.phoneLocation)
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { PhoneTrackBuilder.build(fileName, it) }
                    }
                }
                TrackRepositoryResult.Loaded(track, phoneTrack)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            TrackRepositoryResult.ReadFailed
        }
    }

}
