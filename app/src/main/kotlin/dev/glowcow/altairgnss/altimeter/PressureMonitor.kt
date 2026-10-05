package dev.glowcow.altairgnss.altimeter

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

/** One smoothed reading of the barometer and when the sensor took it, on its own clock. */
data class PressureSample(val hpa: Double, val timeNanos: Long)

/** The barometer, smoothed; it runs only while [samples] is collected. */
class PressureMonitor(context: Context) {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val available: Boolean = sensor != null

    /** Null until the first reading, and always on a phone without the sensor. */
    val samples: Flow<PressureSample?> = if (sensor == null) flowOf<PressureSample?>(null) else callbackFlow<PressureSample?> {
        val smoother = Smoother(SMOOTHING_SECONDS)
        var sentNanos = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = smoother.update(event.values[0].toDouble(), event.timestamp)
                if (event.timestamp - sentNanos >= EMIT_NANOS) {
                    sentNanos = event.timestamp
                    trySend(PressureSample(value, event.timestamp))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate().onStart { emit(null) }

    private companion object {
        // Sensor noise is a few Pa, about 0.3 m; a second of smoothing brings it to centimetres.
        const val SMOOTHING_SECONDS = 1.0
        const val EMIT_NANOS = 250_000_000L
    }
}
