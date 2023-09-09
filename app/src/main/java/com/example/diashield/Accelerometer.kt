package com.example.diashield

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class Accelerometer : Service(), SensorEventListener {
    private val tag = "DiaShield"
    private val captureTime: Long = 40000
    private var accelerometerManager: SensorManager? = null
    private val sensorX = ArrayList<Int>()
    private val sensorY = ArrayList<Int>()
    private val sensorZ = ArrayList<Int>()
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(tag, "onCreate: Accelerometer service is started")
        accelerometerManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val senseAccelerometer = accelerometerManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometerManager!!.registerListener(
            this@Accelerometer,
            senseAccelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(tag, "onStartCommand: Accelerometer service is starting")
        Handler(
            Looper.getMainLooper()
        ).postDelayed(
            {
                stopSelf()
            }, captureTime
        )
        sensorX.clear()
        sensorY.clear()
        sensorZ.clear()
        return START_STICKY
    }

    override fun onSensorChanged(sensorEvent: SensorEvent) {
        Log.d(tag, "onSensorChanged: Capturing data")
        val sensor = sensorEvent.sensor
        if (sensor.type == Sensor.TYPE_ACCELEROMETER) {
            sensorX.add((sensorEvent.values[0]).toInt())
            sensorY.add((sensorEvent.values[1]).toInt())
            sensorZ.add((sensorEvent.values[2]).toInt())
        }
    }

    override fun onDestroy() {
        accelerometerManager!!.unregisterListener(this)
        Log.d(tag, "Accelerometer service is being stopped")
        val thread = Thread {
            val intent = Intent("AccelerometerBroadcastData")
            val b = Bundle()
            b.putIntegerArrayList("accelValuesY", sensorY)
            b.putIntegerArrayList("accelValuesZ", sensorZ)
            b.putIntegerArrayList("accelValuesX", sensorX)
            intent.putExtras(b)
            LocalBroadcastManager.getInstance(this@Accelerometer).sendBroadcast(intent)
        }
        thread.start()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}