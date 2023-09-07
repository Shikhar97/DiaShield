package com.example.diashield

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
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
import com.example.diashield.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
//    private val accelValuesZ: Any = TODO()
    private var rate: Int = 0
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

        cameraExecutor = Executors.newSingleThreadExecutor()
        val extendedFab: Button = findViewById(R.id.extended_fab)

        extendedFab.setOnClickListener {
            val intent = Intent(this, SecondActivity::class.java)
            intent.putExtra("heart_rate", 90.2.toFloat())
            intent.putExtra("resp_rate", 21.2.toFloat())
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

        // Enabling flash
        if (camera.cameraInfo.hasFlashUnit()) {
            camera.cameraControl.enableTorch(true) // or false
        }
        val videoCapture = this.videoCapture ?: return
        viewBinding.measureHeartRate.isEnabled = false
        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
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
                        }, 45000)

                        viewBinding.measureHeartRate.apply {
                            text = getString(R.string.stop_capture_heart_rate)
                            isEnabled = true
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"

                            val videoPath =
                                convertMediaUriToPath(recordEvent.outputResults.outputUri)
                            lifecycleScope.launch(Dispatchers.IO) {
                                val heartRate = calculateHeartRate(videoPath)
                                withContext(Dispatchers.Main) {
                                    println("Accessing UI on ${Thread.currentThread().name}")
                                    viewBinding.heartRateVal.setText(heartRate)
                                }
                            }
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
                                .show()
                            Toast.makeText(baseContext, videoPath, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(
                                TAG, "Video capture ends with error: " +
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

//    private fun callRespiratoryCalculator(): Int {
//        var previousValue: Float
//        var currentValue: Float
//        previousValue = 10f
//        var k = 0
//        for (i in 11..450) {
//            currentValue = kotlin.math.sqrt(
//                accelValuesZ[i].toDouble().pow(2.0) +
//                        accelValuesX[i].toDouble().pow(2.0) +
//                        accelValuesY[i].toDouble().pow(2.0)
//            ).toFloat()
//            if (abs(x = previousValue - currentValue) > 0.15) {
//                k++
//            }
//            previousValue = currentValue
//        }
//        val ret = (k / 45.00)
//
//        return (ret * 30).toInt()
//    }

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
            rate = ((count.toFloat() / 45) * 60).toInt()
            return (rate / 2).toString()
        }

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
                camera = cameraProvider
                    .bindToLifecycle(this, cameraSelector, preview, videoCapture)


            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
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

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).toTypedArray()
    }
}