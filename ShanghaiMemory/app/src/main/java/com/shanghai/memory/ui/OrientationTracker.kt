package com.shanghai.memory.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tracks device compass azimuth [0, 360) using rotation vector (or accelerometer + magnetometer fallback).
 * Smooths circular wrap-around at 0°/360° via vector components so map rotation stays fluid while walking.
 */
class OrientationTracker(
    context: Context,
    private val onAzimuth: (Float) -> Unit
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val accelSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val accelData = FloatArray(3)
    private val magData = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

    private var smoothedSin = 0f
    private var smoothedCos = 0f
    private var initialized = false
    private var lastDelivered = -1f
    private var lastEmitMs = 0L
    private var running = false

    val hasCompass: Boolean
        get() = rotationSensor != null || (accelSensor != null && magSensor != null)

    var currentAzimuth: Float = 0f
        private set

    fun start() {
        if (running || sensorManager == null) return
        running = true
        if (rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        } else if (accelSensor != null && magSensor != null) {
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager?.unregisterListener(this)
        hasAccel = false
        hasMag = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                emitFromRotationMatrix()
            }

            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelData, 0, 3)
                hasAccel = true
                if (hasMag && SensorManager.getRotationMatrix(rotationMatrix, null, accelData, magData)) {
                    emitFromRotationMatrix()
                }
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magData, 0, 3)
                hasMag = true
                if (hasAccel && SensorManager.getRotationMatrix(rotationMatrix, null, accelData, magData)) {
                    emitFromRotationMatrix()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun emitFromRotationMatrix() {
        val displayRotation = currentDisplayRotation()
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        val matrix = if (axisX == SensorManager.AXIS_X && axisY == SensorManager.AXIS_Y) {
            rotationMatrix
        } else {
            SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
            remappedMatrix
        }

        // 结合手机顶部 (+Y) 与背面 (-Z) 在水平面（东, 北）上的投影之和，
        // 无论手机平放手心还是竖直斜握（0°~120°），都能避免 getOrientation 在 90° 处发生万向节翻转。
        val east = matrix[1] - matrix[2]
        val north = matrix[4] - matrix[5]
        val rawDeg = if (east * east + north * north > 1e-6f) {
            ((Math.toDegrees(atan2(east.toDouble(), north.toDouble())).toFloat()) + 360f) % 360f
        } else {
            currentAzimuth
        }
        val rad = Math.toRadians(rawDeg.toDouble())
        val s = sin(rad).toFloat()
        val c = cos(rad).toFloat()

        if (!initialized) {
            smoothedSin = s
            smoothedCos = c
            initialized = true
        } else {
            smoothedSin += ALPHA * (s - smoothedSin)
            smoothedCos += ALPHA * (c - smoothedCos)
        }

        val smoothed = ((Math.toDegrees(atan2(smoothedSin.toDouble(), smoothedCos.toDouble())).toFloat()) + 360f) % 360f
        currentAzimuth = smoothed

        val now = SystemClock.uptimeMillis()
        if (now - lastEmitMs < EMIT_INTERVAL_MS) return
        val delta = angularDiff(smoothed, lastDelivered)
        if (lastDelivered < 0f || delta >= MIN_DELTA_DEG) {
            lastDelivered = smoothed
            lastEmitMs = now
            onAzimuth(smoothed)
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { appContext.display?.rotation }
                .getOrNull()
                ?.let { return it }
        }
        val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        return wm?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }

    private fun angularDiff(a: Float, b: Float): Float {
        if (b < 0f) return 360f
        val diff = abs(a - b) % 360f
        return if (diff > 180f) 360f - diff else diff
    }

    private companion object {
        const val ALPHA = 0.20f
        const val EMIT_INTERVAL_MS = 50L
        const val MIN_DELTA_DEG = 1.2f
    }
}
