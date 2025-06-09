package com.example.kinetizk.demo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.min

data class SensorReading(
    val timestamp: Long,              // ns
    val accelX: Double, val accelY: Double, val accelZ: Double,
    val gyroX: Double,  val gyroY: Double,  val gyroZ: Double
)

/** 터치 앞뒤 50 ms/250 ms 윈도 구성용 링버퍼·스트림러 */
class SensorCollector(ctx: Context) : SensorEventListener {

    private val mgr = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = mgr.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyro  = mgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val ring = ArrayDeque<SensorReading>(300)
    private var lastAccel = FloatArray(3)
    private var lastGyro  = FloatArray(3)

    private var callback: (SensorReading) -> Unit = {}

    fun register() {
        mgr.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST)
        mgr.registerListener(this, gyro,  SensorManager.SENSOR_DELAY_FASTEST)
    }
    fun unregister()  { mgr.unregisterListener(this) }

    /* ---------- 윈도 수집 ---------- */

    fun beginWindow(downNs: Long, cb: (SensorReading) -> Unit) {
        // DOWN 시각보다 50 ms 전 이후의 버퍼 샘플 먼저 플러시
        val cutoff = downNs - 50_000_000L
        ring.filter { it.timestamp >= cutoff }.forEach(cb)

        callback = { r ->
            cb(r)
            enqueue(r)
        }
    }
    fun endWindow() { callback = {} }

    /* ---------- SensorEventListener ---------- */

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_LINEAR_ACCELERATION -> lastAccel = e.values.clone()
            Sensor.TYPE_GYROSCOPE           -> lastGyro  = e.values.clone()
        }
        val reading = SensorReading(
            timestamp = e.timestamp,
            accelX = lastAccel[0].toDouble(), accelY = lastAccel[1].toDouble(), accelZ = lastAccel[2].toDouble(),
            gyroX  = lastGyro[0].toDouble(),  gyroY  = lastGyro[1].toDouble(),  gyroZ  = lastGyro[2].toDouble()
        )
        enqueue(reading)
        callback(reading)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun enqueue(r: SensorReading) {
        if (ring.size == 300) ring.removeFirst()
        ring.addLast(r)
    }
}
