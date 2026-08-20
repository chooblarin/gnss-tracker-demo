package com.example.qz1sample

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Qz1RecordingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val indexScheduler by lazy { WorkManagerSessionIndexScheduler(applicationContext) }

    private var recordingJob: Deferred<RecordingOutcome?>? = null
    private var activeResources: RecordingSessionResources? = null
    private var stopReason: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> serviceScope.launch {
                lifecycleMutex.withLock {
                    startRecordingLocked(intent, startId)
                }
            }
            ACTION_STOP -> serviceScope.launch {
                lifecycleMutex.withLock {
                    completeRecordingLocked(
                        outcome = RecordingOutcome.Stopped("manual"),
                        stopStartId = startId,
                        joinJob = true
                    )
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runBlocking {
            lifecycleMutex.withLock {
                if (
                    activeResources != null ||
                    recordingJob != null
                ) {
                    completeRecordingLocked(
                        outcome = RecordingOutcome.Stopped("service destroyed"),
                        stopStartId = null,
                        joinJob = false
                    )
                }
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private suspend fun startRecordingLocked(intent: Intent, startId: Int) {
        val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        val deviceLabel = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: address ?: "QZ1"

        if (
            recordingJob != null ||
            activeResources != null
        ) {
            completeRecordingLocked(
                outcome = RecordingOutcome.Stopped("restart"),
                stopStartId = null,
                joinJob = true
            )
        }
        stopReason = null

        if (address == null) {
            completeRecordingLocked(
                outcome = RecordingOutcome.Failed("Missing device address"),
                stopStartId = startId,
                joinJob = false
            )
            return
        }
        if (!hasBluetoothConnectPermission()) {
            completeRecordingLocked(
                RecordingOutcome.Failed("Nearby devices permission required"),
                stopStartId = startId,
                joinJob = false
            )
            return
        }
        startInForeground(deviceLabel, "preparing", recordsPhoneGnss = false)

        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            completeRecordingLocked(
                outcome = RecordingOutcome.Failed("Bluetooth is not ready"),
                stopStartId = startId,
                joinJob = false
            )
            return
        }

        val device = try {
            adapter.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            completeRecordingLocked(
                outcome = RecordingOutcome.Failed("Invalid device address"),
                stopStartId = startId,
                joinJob = false
            )
            return
        }

        val resources = try {
            RecordingSessionResources.create(
                context = this,
                deviceLabel = deviceLabel
            )
        } catch (error: Exception) {
            completeRecordingLocked(
                outcome = RecordingOutcome.Failed(
                    error.message ?: "Could not create NMEA log"
                ),
                stopStartId = startId,
                joinJob = false
            )
            return
        }
        activeResources = resources
        Qz1RecordingStatusStore.connecting(deviceLabel, resources.fileName)
        when (
            val phoneResult = resources.startPhoneGnss(
                context = this,
                onSnapshot = Qz1RecordingStatusStore::updatePhoneGnss,
                onFailure = { reason ->
                    handlePhoneRecordingFailure(resources, deviceLabel, reason)
                }
            )
        ) {
            is PhoneGnssStartResult.Started -> Unit
            is PhoneGnssStartResult.Unavailable -> {
                Qz1RecordingStatusStore.phoneUnavailable(phoneResult.reason)
            }
        }
        if (resources.recordsPhoneGnss) {
            try {
                startInForeground(
                    deviceLabel,
                    resources.fileName,
                    recordsPhoneGnss = true
                )
            } catch (error: RuntimeException) {
                resources.discardPhoneGnss()
                Qz1RecordingStatusStore.phoneUnavailable(
                    error.message ?: "location foreground service unavailable"
                )
                startInForeground(
                    deviceLabel,
                    resources.fileName,
                    recordsPhoneGnss = false
                )
            }
        } else {
            startInForeground(
                deviceLabel,
                resources.fileName,
                recordsPhoneGnss = false
            )
        }

        val job = serviceScope.async {
            runRecording(device, deviceLabel, resources)
        }
        recordingJob = job
        serviceScope.launch {
            val outcome = job.await()
            if (outcome != null) {
                lifecycleMutex.withLock {
                    if (recordingJob === job) {
                        completeRecordingLocked(
                            outcome = outcome,
                            stopStartId = startId,
                            joinJob = false
                        )
                    }
                }
            }
        }
    }

    private fun handlePhoneRecordingFailure(
        resources: RecordingSessionResources,
        deviceLabel: String,
        reason: String
    ) {
        serviceScope.launch {
            lifecycleMutex.withLock {
                if (activeResources !== resources) return@withLock
                try {
                    resources.stopPhoneGnss()
                } catch (error: Exception) {
                    Log.w(TAG, "Could not stop Phone GNSS after write failure", error)
                }
                Qz1RecordingStatusStore.phoneUnavailable(reason)
                try {
                    startInForeground(
                        deviceLabel = deviceLabel,
                        fileName = resources.fileName,
                        recordsPhoneGnss = false
                    )
                } catch (error: RuntimeException) {
                    Log.w(TAG, "Could not update foreground service type", error)
                }
            }
        }
    }

    private suspend fun runRecording(
        device: BluetoothDevice,
        deviceLabel: String,
        resources: RecordingSessionResources
    ): RecordingOutcome? {
        val logWriter = resources.nmeaWriter
        val lineBuffer = NmeaLineBuffer()
        val sppSession = SppSession(device)
        if (!resources.attach(sppSession)) return null

        var receivedBytes = 0
        var savedLines = 0
        var lastStatusUpdate = 0L
        var lastNotificationUpdate = 0L
        var gnssSnapshot = GnssSnapshot()
        val pendingRaw = StringBuilder()
        val pendingNmea = mutableListOf<String>()

        try {
            sppSession.open()
            Qz1RecordingStatusStore.connected(deviceLabel)
            updateNotification(deviceLabel, receivedBytes, savedLines, logWriter.file.name)

            val reason = sppSession.readLoop { chunk ->
                receivedBytes += chunk.byteCount
                pendingRaw.append(chunk.text)

                val lines = lineBuffer.append(chunk.text)
                if (lines.isNotEmpty()) {
                    logWriter.appendLines(lines)
                    savedLines += lines.size
                    pendingNmea.addAll(lines)
                    val nowMillis = System.currentTimeMillis()
                    for (line in lines) {
                        val result = NmeaParser.parse(line)
                        if (result is NmeaParseResult.Parsed) {
                            gnssSnapshot = GnssStateReducer.reduce(
                                snapshot = gnssSnapshot,
                                event = result.event,
                                updatedAtMillis = nowMillis
                            )
                        }
                    }
                }

                val now = SystemClock.elapsedRealtime()
                if (now - lastStatusUpdate >= STATUS_UPDATE_MS) {
                    flushStatus(receivedBytes, savedLines, pendingRaw, pendingNmea, gnssSnapshot)
                    lastStatusUpdate = now
                }
                if (now - lastNotificationUpdate >= NOTIFICATION_UPDATE_MS) {
                    updateNotification(deviceLabel, receivedBytes, savedLines, logWriter.file.name)
                    lastNotificationUpdate = now
                }
            }

            flushStatus(receivedBytes, savedLines, pendingRaw, pendingNmea, gnssSnapshot)
            return if (reason == ReadStopReason.StreamClosed && stopReason == null) {
                RecordingOutcome.Stopped("stream closed")
            } else {
                null
            }
        } catch (_: CancellationException) {
            flushStatus(receivedBytes, savedLines, pendingRaw, pendingNmea, gnssSnapshot)
            return null
        } catch (error: IOException) {
            flushStatus(receivedBytes, savedLines, pendingRaw, pendingNmea, gnssSnapshot)
            return if (stopReason == null) {
                RecordingOutcome.Failed("Connection failed: ${error.message ?: error.javaClass.simpleName}")
            } else {
                null
            }
        } catch (error: SecurityException) {
            flushStatus(receivedBytes, savedLines, pendingRaw, pendingNmea, gnssSnapshot)
            return if (stopReason == null) {
                RecordingOutcome.Failed("Bluetooth permission error: ${error.message ?: "permission denied"}")
            } else {
                null
            }
        }
    }

    private fun flushStatus(
        receivedBytes: Int,
        savedLines: Int,
        pendingRaw: StringBuilder,
        pendingNmea: MutableList<String>,
        gnssSnapshot: GnssSnapshot
    ) {
        if (pendingRaw.isEmpty() && pendingNmea.isEmpty()) return

        Qz1RecordingStatusStore.updateReceived(
            byteCount = receivedBytes,
            savedLineCount = savedLines,
            rawChunk = pendingRaw.toString(),
            nmeaLines = pendingNmea.toList(),
            gnss = gnssSnapshot
        )
        pendingRaw.clear()
        pendingNmea.clear()
    }

    private suspend fun completeRecordingLocked(
        outcome: RecordingOutcome,
        stopStartId: Int?,
        joinJob: Boolean
    ) {
        stopReason = outcome.message
        activeResources?.closeSppSession()
        if (joinJob) {
            recordingJob?.cancelAndJoin()
        } else {
            recordingJob?.cancel()
        }
        val closedFileName = closeResources()
        publishOutcome(outcome)
        closedFileName?.let { fileName ->
            try {
                indexScheduler.enqueue(fileName)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not schedule metadata indexing", error)
            }
        }
        stopStartId?.let(::stopSelfResult)
    }

    private fun closeResources(): String? {
        val resources = activeResources
        val fileName = resources?.fileName
        try {
            resources?.close()
        } catch (error: Exception) {
            Log.w(TAG, "Could not close recording resources", error)
        }
        activeResources = null
        recordingJob = null
        return fileName
    }

    private fun publishOutcome(outcome: RecordingOutcome) {
        when (outcome) {
            is RecordingOutcome.Failed -> Qz1RecordingStatusStore.failed(outcome.message)
            is RecordingOutcome.Stopped -> Qz1RecordingStatusStore.stopped(outcome.reason)
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startInForeground(
        deviceLabel: String,
        fileName: String,
        recordsPhoneGnss: Boolean
    ) {
        val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            if (recordsPhoneGnss) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        startForeground(
            NOTIFICATION_ID,
            buildNotification(deviceLabel, 0, 0, fileName),
            serviceType
        )
    }

    private fun updateNotification(
        deviceLabel: String,
        receivedBytes: Int,
        savedLines: Int,
        fileName: String
    ) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(deviceLabel, receivedBytes, savedLines, fileName)
        )
    }

    private fun buildNotification(
        deviceLabel: String,
        receivedBytes: Int,
        savedLines: Int,
        fileName: String
    ): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, Qz1RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("QZ1 recording")
            .setContentText("$deviceLabel / $savedLines lines / $receivedBytes bytes")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Saving to $fileName"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "QZ1 Recording",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START = "com.example.qz1sample.START_RECORDING"
        private const val ACTION_STOP = "com.example.qz1sample.STOP_RECORDING"
        private const val EXTRA_DEVICE_ADDRESS = "device_address"
        private const val EXTRA_DEVICE_NAME = "device_name"
        private const val CHANNEL_ID = "qz1_recording"
        private const val NOTIFICATION_ID = 1001
        private const val STATUS_UPDATE_MS = 250L
        private const val NOTIFICATION_UPDATE_MS = 1_000L
        private const val TAG = "Qz1RecordingService"

        fun start(context: Context, address: String, deviceName: String) {
            val intent = Intent(context, Qz1RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_DEVICE_ADDRESS, address)
                .putExtra(EXTRA_DEVICE_NAME, deviceName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, Qz1RecordingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}

private sealed interface RecordingOutcome {
    val message: String

    data class Stopped(val reason: String) : RecordingOutcome {
        override val message: String = reason
    }

    data class Failed(override val message: String) : RecordingOutcome
}
