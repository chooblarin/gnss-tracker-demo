package com.example.qz1sample

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import androidx.core.content.ContextCompat
import java.io.Closeable
import java.nio.file.FileAlreadyExistsException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

sealed interface PhoneGnssStartResult {
    data class Started(val recorder: PhoneGnssRecorder) : PhoneGnssStartResult
    data class Unavailable(val reason: String) : PhoneGnssStartResult
}

class PhoneGnssRecorder private constructor(
    private val locationManager: LocationManager,
    private val writer: PhoneLocationLogWriter,
    private val onSnapshot: (PhoneGnssSnapshot) -> Unit,
    onFailure: (String) -> Unit
) : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private var satellites = PhoneSatelliteSnapshot()
    private var savedPoints = 0
    private var locationRegistered = false
    private var statusRegistered = false
    private var closed = false
    private val writeGuard = PhoneLocationWriteGuard(writer::append, onFailure)

    private val locationListener = LocationListener(::recordLocation)
    private val statusCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            val usedIndices = (0 until status.satelliteCount).filter(status::usedInFix)
            satellites = PhoneSatelliteSnapshot(
                visible = status.satelliteCount,
                used = usedIndices.size,
                gpsUsed = usedIndices.count {
                    status.getConstellationType(it) == GnssStatus.CONSTELLATION_GPS
                },
                qzssUsed = usedIndices.count {
                    status.getConstellationType(it) == GnssStatus.CONSTELLATION_QZSS
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun start() {
        val request = LocationRequest.Builder(UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(UPDATE_INTERVAL_MS)
            .setMinUpdateDistanceMeters(0f)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .build()
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            request,
            executor,
            locationListener
        )
        locationRegistered = true
        statusRegistered = locationManager.registerGnssStatusCallback(executor, statusCallback)
    }

    private fun recordLocation(location: Location) {
        val sample = PhoneLocationSample(
            epochMillis = location.time,
            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = location.altitude.takeIf { location.hasAltitude() },
            accuracyMeters = location.accuracy.toDouble().takeIf { location.hasAccuracy() },
            speedMetersPerSecond = location.speed.toDouble().takeIf { location.hasSpeed() },
            bearingDegrees = location.bearing.toDouble().takeIf { location.hasBearing() },
            satellitesVisible = satellites.visible,
            satellitesUsed = satellites.used,
            gpsUsed = satellites.gpsUsed,
            qzssUsed = satellites.qzssUsed
        )
        if (!writeGuard.append(sample)) return
        savedPoints += 1
        onSnapshot(
            PhoneGnssSnapshot(
                latitude = sample.latitude,
                longitude = sample.longitude,
                accuracyMeters = sample.accuracyMeters,
                savedPoints = savedPoints,
                satellites = satellites
            )
        )
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        if (closed) return
        closed = true
        if (locationRegistered) locationManager.removeUpdates(locationListener)
        if (statusRegistered) locationManager.unregisterGnssStatusCallback(statusCallback)
        executor.shutdown()
        if (!executor.awaitTermination(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
        writer.close()
    }

    internal fun discard() {
        runCatching { close() }
        writer.discard()
    }

    companion object {
        fun tryStart(
            context: Context,
            nmeaFileName: String,
            onSnapshot: (PhoneGnssSnapshot) -> Unit,
            onFailure: (String) -> Unit
        ): PhoneGnssStartResult {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val locationManager = context.getSystemService(LocationManager::class.java)
            val gpsProviderEnabled = runCatching {
                locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            }.getOrDefault(false)
            val unavailableReason = PhoneGnssStartPolicy.unavailableReason(
                hasPreciseLocationPermission = hasPermission,
                hasLocationService = locationManager != null,
                isGpsProviderEnabled = gpsProviderEnabled
            )
            if (unavailableReason != null) {
                return PhoneGnssStartResult.Unavailable(unavailableReason)
            }
            checkNotNull(locationManager)

            var recorder: PhoneGnssRecorder? = null
            return try {
                recorder = PhoneGnssRecorder(
                    locationManager = locationManager,
                    writer = PhoneLocationLogWriter.create(context, nmeaFileName),
                    onSnapshot = onSnapshot,
                    onFailure = onFailure
                )
                recorder.start()
                PhoneGnssStartResult.Started(recorder)
            } catch (_: FileAlreadyExistsException) {
                PhoneGnssStartResult.Unavailable("phone comparison file already exists")
            } catch (error: Exception) {
                recorder?.discard()
                PhoneGnssStartResult.Unavailable(
                    error.message ?: "could not start phone GNSS"
                )
            }
        }

        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val CLOSE_TIMEOUT_SECONDS = 2L
    }
}

internal class PhoneLocationWriteGuard(
    private val write: (PhoneLocationSample) -> Unit,
    private val onFailure: (String) -> Unit
) {
    private val failed = AtomicBoolean(false)

    fun append(sample: PhoneLocationSample): Boolean {
        if (failed.get()) return false
        return try {
            write(sample)
            true
        } catch (error: Exception) {
            if (failed.compareAndSet(false, true)) {
                onFailure("phone location log write failed: ${error.javaClass.simpleName}")
            }
            false
        }
    }
}

internal object PhoneGnssStartPolicy {
    fun unavailableReason(
        hasPreciseLocationPermission: Boolean,
        hasLocationService: Boolean,
        isGpsProviderEnabled: Boolean
    ): String? {
        return when {
            !hasPreciseLocationPermission -> "precise location permission not granted"
            !hasLocationService -> "location service unavailable"
            !isGpsProviderEnabled -> "phone location services are off"
            else -> null
        }
    }
}
