package com.example.htv11.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// Real hardware presence check via the pressure sensor itself — NOT the same
// thing as Location.hasAltitude(), which only reflects whether a given GPS fix
// happened to include altitude data (GPS can produce altitude with zero
// barometer hardware involved, and a phone with a barometer can still return
// a fix with no altitude on a bad read).
class BarometerReader(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val hasBarometerHardware: Boolean get() = pressureSensor != null

    // Raw atmospheric pressure in hPa (millibars). Emits nothing if this device
    // has no pressure sensor.
    fun pressureReadings(): Flow<Float> = callbackFlow {
        val sensor = pressureSensor
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
