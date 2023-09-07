package com.example.diashield

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.diashield.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private val captureTime: Long = 2500
    private lateinit var heartRate: String
    private val rootPath = Environment.getExternalStorageDirectory().path
    private val tag = "CameraXApp"
    private var rate: Float = 0.0f
    private lateinit var camera: Camera
    private lateinit var viewBinding: ActivityMainBinding
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startCamera()
            }
        }
    companion object {
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()
    }

    class RespiratoryRateDetector internal constructor(
        private var accelValuesX: java.util.ArrayList<Int>,
        private var accelValuesY: java.util.ArrayList<Int>,
        private var accelValuesZ: java.util.ArrayList<Int>
    ) :
        Runnable {
        var respiratoryRate = 0f

        override fun run() {
            var previousValue: Float
            var currentValue: Float
            previousValue = 10f
            var k = 0
            for (i in 11..450) {
                currentValue = sqrt(
                    accelValuesZ[i].toDouble().pow(2.0) + accelValuesX[i].toDouble()
                        .pow(2.0) + accelValuesY[i].toDouble().pow(2.0)
                ).toFloat()
                if (abs(x = previousValue - currentValue) > 0.15) {
                    k++
                }
                previousValue = currentValue
            }
            val ret = (k / 45.00)
            respiratoryRate = (ret * 30).toFloat()
            Log.i("CameraXApp", "Respiratory rate: $respiratoryRate")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set up the listeners for video capture
        viewBinding.measureHeartRate.setOnClickListener { captureVideo() }
        viewBinding.measureRespRate.setOnClickListener {
            Toast.makeText(
                baseContext,
                "Please lay the phone on abdomen for 45 seconds",
                Toast.LENGTH_LONG
            ).show()
            val intentAccelerometer = Intent(baseContext, Accelerometer::class.java)
            startService(intentAccelerometer)

        }
        LocalBroadcastManager.getInstance(this@MainActivity)
            .registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val b = intent.extras
                    val runnable = RespiratoryRateDetector(
                        b!!.getIntegerArrayList("accelValuesX") ?: ArrayList(),
                        b!!.getIntegerArrayList("accelValuesY") ?: ArrayList(),
                        b!!.getIntegerArrayList("accelValuesZ") ?: ArrayList()
                    )
                    val thread = Thread(runnable)
                    thread.start()
                    try {
                        thread.join()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    viewBinding.respRateVal.setText(runnable.respiratoryRate.toString())
                    Toast.makeText(this@MainActivity, "Respiratory rate calculated!", Toast.LENGTH_SHORT).show()
                    b.clear()
                    System.gc()
                }
            }, IntentFilter("AccelerometerDataBroadcasting"))

        cameraExecutor = Executors.newSingleThreadExecutor()
        val extendedFab: Button = findViewById(R.id.extended_fab)


        extendedFab.setOnClickListener {
            val intent = Intent(this, SecondActivity::class.java)
            intent.putExtra("heart_rate", viewBinding.heartRateVal.text)
            intent.putExtra("resp_rate", viewBinding.respRateVal.text)
            startActivity(intent)
        }

    }

    private fun convertMediaUriToPath(uri: Uri?): String {
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = contentResolver.query(uri!!, proj, null, null, null)
        val columnIndex = cursor!!.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()
        val path = cursor.getString(columnIndex)
        cursor.close()
        return path
    }


    private fun captureVideo() {
        Toast.makeText(
            baseContext,
            "Please place your finger on the back camera lens for 45 seconds",
            Toast.LENGTH_LONG
        ).show()

        // Enabling flash
        if (camera.cameraInfo.hasFlashUnit()) {
            camera.cameraControl.enableTorch(true)
        }
        val videoCapture = this.videoCapture ?: return
        viewBinding.measureHeartRate.isEnabled = false
        val curRecording = recording
        
        // create and start a new recording session
        val name = "$rootPath/heart_rate_video.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.root.postDelayed({
                            curRecording?.stop()
                            recording = null
                            // Disable flash after capturing
                            if (camera.cameraInfo.hasFlashUnit()) {
                                camera.cameraControl.enableTorch(false)
                            }
                        }, captureTime)

                        viewBinding.measureHeartRate.apply {
                            text = getString(R.string.capture_heart_rate)
                            isEnabled = false
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"

                            val videoPath =
                                convertMediaUriToPath(recordEvent.outputResults.outputUri)
                            lifecycleScope.launch(Dispatchers.IO) {
                                calculateHeartRate(videoPath)
                                withContext(Dispatchers.Main) {
                                    viewBinding.heartRateVal.setText(heartRate)
                                    Log.i("CameraXApp", "Heart rate: $heartRate")
                                    Toast.makeText(this@MainActivity, "Heart rate calculated!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            Log.d(tag, videoPath)
                            Log.d(tag, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(
                                tag, "Video capture ends with error: " +
                                        "${recordEvent.error}"
                            )
                        }
                        viewBinding.measureHeartRate.apply {
                            text = getString(R.string.capture_heart_rate)
                            isEnabled = true
                        }
                    }
                }
            }
    }

    private fun calculateHeartRate(vararg params: String?): String {
        val retriever = MediaMetadataRetriever()
        val frameList = ArrayList<Bitmap>()
        try {

            retriever.setDataSource(params[0])
            val duration =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
            val aDuration = duration!!.toInt()
            var i = 10
            while (i < aDuration) {
                val bitmap = retriever.getFrameAtIndex(i)
                frameList.add(bitmap!!)
                i += 5
            }
        } catch (_: Exception) {
        } finally {
            retriever.release()
            var redBucket: Long
            var pixelCount: Long = 0
            val a = mutableListOf<Long>()
            for (i in frameList) {
                redBucket = 0
                for (y in 550 until 650) {
                    for (x in 550 until 650) {
                        val c: Int = i.getPixel(x, y)
                        pixelCount++
                        redBucket += Color.red(c) + Color.blue(c) + Color.green(c)
                    }
                }
                a.add(redBucket)
            }
            val b = mutableListOf<Long>()
            for (i in 0 until a.lastIndex - 5) {
                val temp =
                    (a.elementAt(i) + a.elementAt(i + 1) + a.elementAt(i + 2) + a.elementAt(
                        i + 3
                    ) + a.elementAt(
                        i + 4
                    )) / 4
                b.add(temp)
            }
            var x = b.elementAt(0)
            var count = 0
            for (i in 1 until b.lastIndex) {
                val p = b.elementAt(i)
                if ((p - x) > 3500) {
                    count += 1
                }
                x = b.elementAt(i)
            }
            rate = (count.toFloat() / 45) * 60
            heartRate = (rate / 2).toString()
        }
        return (rate / 2).toString()

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)


            } catch (exc: Exception) {
                Log.e(tag, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}