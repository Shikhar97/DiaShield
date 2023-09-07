package com.example.diashield

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class Accelerometer : Service(), SensorEventListener {
    private var accelerometerManager: SensorManager? = null
    val accelValuesX = ArrayList<Int>()
    val accelValuesY = ArrayList<Int>()
    val accelValuesZ = ArrayList<Int>()
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d("CameraXApp", "onCreate: Accelerometer service is started")
        accelerometerManager = getSystemService(SENSOR_SERVICE) as SensorManager
        val senseAccelerometer = accelerometerManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometerManager!!.registerListener(
            this@Accelerometer,
            senseAccelerometer,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d("CameraXApp", "onStartCommand: Accelerometer service is starting")

        accelValuesX.clear()
        accelValuesY.clear()
        accelValuesZ.clear()
        return START_STICKY
    }

    override fun onSensorChanged(sensorEvent: SensorEvent) {
        Log.d("CameraXApp", "onSensorChanged: Capturing data")

        val sensor = sensorEvent.sensor
        if (sensor.type == Sensor.TYPE_ACCELEROMETER) {
            accelValuesX.add((sensorEvent.values[0] * 100).toInt())
            accelValuesY.add((sensorEvent.values[1] * 100).toInt())
            accelValuesZ.add((sensorEvent.values[2] * 100).toInt())
            if (accelValuesX.size >= 480) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        accelerometerManager!!.unregisterListener(this)
        Log.d("CameraXApp", "Accelerometer service is being stopped")
        val thread = Thread {
            val intent = Intent("AccelerometerDataBroadcasting")
            val b = Bundle()
            b.putIntegerArrayList("accelValuesY", accelValuesY)
            b.putIntegerArrayList("accelValuesZ", accelValuesZ)
            b.putIntegerArrayList("accelValuesX", accelValuesX)
            intent.putExtras(b)
            LocalBroadcastManager.getInstance(this@Accelerometer).sendBroadcast(intent)
        }
        thread.start()
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
}