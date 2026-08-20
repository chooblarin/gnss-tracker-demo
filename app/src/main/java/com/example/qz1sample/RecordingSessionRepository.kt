package com.example.qz1sample

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordingSessionRepository(
    private val context: Context,
    private val metadataStore: SessionMetadataStore = JsonSessionMetadataStore(context),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun scan(): List<RecordingSession> = withContext(ioDispatcher) {
        RecordingSessionFileStore.list(context).map { files ->
            val summary = metadataStore.read(files.fileName)
                ?.takeIf { it.isValidFor(files.primary) }
                ?.summary
            RecordingSession(
                files = files,
                summary = summary
            )
        }
    }

    suspend fun index(fileName: String): SessionIndexResult = withContext(ioDispatcher) {
        val logFile = RecordingSessionFileStore.find(context, fileName)?.primary
            ?: return@withContext SessionIndexResult.SourceMissing
        val cached = metadataStore.read(fileName)
        if (cached?.isValidFor(logFile) == true) {
            return@withContext SessionIndexResult.Current
        }

        try {
            val track = NmeaLogStore.readLines(context, fileName) { lines ->
                TrackBuilder.build(fileName, lines)
            } ?: return@withContext SessionIndexResult.SourceMissing
            metadataStore.write(
                SessionMetadataIndexer.build(
                    track = track,
                    source = SourceFingerprint.from(logFile)
                )
            )
            SessionIndexResult.Indexed
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            SessionIndexResult.Failed(error)
        }
    }

    suspend fun delete(fileName: String): Boolean = withContext(ioDispatcher) {
        metadataStore.delete(fileName)
        RecordingSessionFileStore.delete(context, fileName)
    }
}

sealed interface SessionIndexResult {
    data object Indexed : SessionIndexResult
    data object Current : SessionIndexResult
    data object SourceMissing : SessionIndexResult
    data class Failed(val cause: Exception) : SessionIndexResult
}
