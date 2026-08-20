package com.example.qz1sample

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.IOException

interface SessionIndexScheduler {
    fun enqueue(fileName: String)
}

class WorkManagerSessionIndexScheduler(context: Context) : SessionIndexScheduler {
    private val appContext = context.applicationContext

    override fun enqueue(fileName: String) {
        val request = OneTimeWorkRequestBuilder<SessionIndexWorker>()
            .setInputData(workDataOf(SessionIndexWorker.INPUT_FILE_NAME to fileName))
            .addTag(SESSION_INDEX_WORK_TAG)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            sessionIndexWorkName(fileName),
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

class SessionIndexWorker(
    appContext: Context,
    parameters: WorkerParameters
) : CoroutineWorker(appContext, parameters) {
    private val repository = RecordingSessionRepository(appContext)

    override suspend fun doWork(): Result {
        val fileName = inputData.getString(INPUT_FILE_NAME) ?: return Result.failure()
        return when (val result = repository.index(fileName)) {
            SessionIndexResult.Indexed,
            SessionIndexResult.Current,
            SessionIndexResult.SourceMissing -> Result.success()
            is SessionIndexResult.Failed -> {
                if (result.cause is IOException) retryOrFail() else Result.failure()
            }
        }
    }

    private fun retryOrFail(): Result {
        return if (runAttemptCount < MAX_RETRY_COUNT) Result.retry() else Result.failure()
    }

    internal companion object {
        const val INPUT_FILE_NAME = "file_name"
        const val MAX_RETRY_COUNT = 3
    }
}

internal fun sessionIndexWorkName(fileName: String): String {
    return "qz1-index:$fileName"
}

internal const val SESSION_INDEX_WORK_TAG = "qz1-session-index"
