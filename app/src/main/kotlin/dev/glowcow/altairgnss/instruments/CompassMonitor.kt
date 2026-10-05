package dev.glowcow.altairgnss.instruments

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOf
import kotlin.math.sqrt

data class CompassReading(
    /** Where the top of the screen points, degrees from magnetic north. */
    val magnetic: Float,
    val reliable: Boolean,
    /** Degrees the top edge is raised above level, for a phone held upright in the hand. */
    val pitch: Float,
    /** Degrees the right edge is lowered below level. */
    val roll: Float,
    /** Strength of the magnetic field at the phone, microtesla; null without a magnetometer reading. */
    val fieldMicroTesla: Float?,
)

/** The fused rotation sensor as a compass; it runs only while [reading] is collected. */
class CompassMonitor(context: Context) {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
    private val rotation: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnetometer: Sensor? = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val available: Boolean = rotation != null

    val reading: Flow<CompassReading?> = if (rotation == null) flowOf<CompassReading?>(null) else callbackFlow<CompassReading?> {
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        var reliable = true
        var field: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    val (x, y, z) = event.values
                    field = sqrt(x * x + y * y + z * z)
                    return
                }
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                // The sensor knows the device, not the screen: a turned screen shifts its top by quarter turns.
                val azimuth = Math.toDegrees(angles[0].toDouble()).toFloat() + (display?.rotation ?: 0) * 90f
                trySend(
                    CompassReading(
                        magnetic = Heading.normalize(azimuth),
                        reliable = reliable,
                        pitch = -Math.toDegrees(angles[1].toDouble()).toFloat(),
                        roll = Math.toDegrees(angles[2].toDouble()).toFloat(),
                        fieldMicroTesla = field,
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_ROTATION_VECTOR) reliable = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            }
        }
        manager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        if (magnetometer != null) manager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()
}
