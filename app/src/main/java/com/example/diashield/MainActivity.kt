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
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.diashield.databinding.ActivityMainBinding
import com.opencsv.CSVWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {

    private val captureTime: Long = 40000
    private var heartRate = "0"
    private var respRate = "0"
    private val rootPath = Environment.getExternalStorageDirectory().path
    private val tag = "DiaShield"
    private var rate: Float = 0.0f
    private lateinit var camera: Camera
    private lateinit var viewBinding: ActivityMainBinding
//    private var dialogBox: AlertDialog? = null
    private var layoutMainMenu: ConstraintLayout? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var progressBar: ProgressBar? = null
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
        private val tag = "DiaShield"
        private val rootPath = Environment.getExternalStorageDirectory().path

        private fun saveToCSV(list: ArrayList<Int>, location: String) {
            val file = File(location)
            try {
                val newFile = FileWriter(file)
                val csvWr = CSVWriter(newFile)
                val headerRow = arrayOf("Index", "Data")
                csvWr.writeNext(headerRow)
                for ((i, j) in list.indices.withIndex()) {
                    val rowData = arrayOf(i.toString() + "", list[j].toString() + "")
                    csvWr.writeNext(rowData)
                }
                csvWr.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        override fun run() {
            saveToCSV(accelValuesZ, "$rootPath/accelValuesZ.csv")
            saveToCSV(accelValuesY, "$rootPath/accelValuesY.csv")
            saveToCSV(accelValuesX, "$rootPath/accelValuesX.csv")
            Log.d(tag, accelValuesY.size.toString())
            Log.d(tag, accelValuesX.size.toString())
            Log.d(tag, accelValuesZ.size.toString())

            var previousValue: Float
            var currentValue: Float
            previousValue = 10f
            var k = 0
            for (i in 1..<accelValuesX.size) {
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
            Log.i(tag, "Respiratory rate: $respiratoryRate")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        layoutMainMenu = findViewById(R.id.background)
        layoutMainMenu!!.background.alpha = 0
        progressBar = findViewById(R.id.progressBar)
        val intentAccelerometer = Intent(baseContext, Accelerometer::class.java)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                stopService(intentAccelerometer)
            }
        })
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Set up the listeners for video capture
        viewBinding.measureHeartRate.setOnClickListener {
            Toast.makeText(
                baseContext,
                "Please place your finger on the back camera lens for 45 seconds",
                Toast.LENGTH_LONG
            ).show()
            captureVideo()
        }
        viewBinding.measureRespRate.setOnClickListener {
            Toast.makeText(
                baseContext,
                "Please lay the phone on abdomen for 45 seconds",
                Toast.LENGTH_LONG
            ).show()
            viewBinding.measureRespRate.isEnabled = false
            startService(intentAccelerometer)
            window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
//            dialogBox = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
//                .setIcon(R.drawable.baseline_add_24)
//                .setTitle(resources.getString(R.string.calculating))
//                .setMessage("")
//                .setCancelable(false)
//                .setNeutralButton(resources.getString(R.string.cancel)) { dialog, _ ->
//                    dialog.dismiss()
//                    stopService(intentAccelerometer)
//                }
//                .show()
            progressBar!!.visibility = View.VISIBLE
//            viewBinding.calculating.visibility = View.VISIBLE
            layoutMainMenu!!.background.alpha = 200


        }
        LocalBroadcastManager.getInstance(this@MainActivity)
            .registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    Toast.makeText(
                        baseContext,
                        "Calculating respiratory rate ",
                        Toast.LENGTH_LONG
                    ).show()
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
                    respRate = runnable.respiratoryRate.toString()
                    Toast.makeText(
                        this@MainActivity,
                        "Respiratory rate calculated!",
                        Toast.LENGTH_SHORT
                    ).show()
                    viewBinding.measureRespRate.isEnabled = true
                    progressBar!!.visibility = View.INVISIBLE
//                    viewBinding.calculating.visibility = View.INVISIBLE
                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    layoutMainMenu!!.background.alpha = 0
                    b.clear()
                    System.gc()
                }
            }, IntentFilter("AccelerometerDataBroadcasting"))

        cameraExecutor = Executors.newSingleThreadExecutor()
        val extendedFab: Button = findViewById(R.id.extended_fab)


        extendedFab.setOnClickListener {
            val intent = Intent(this, SecondActivity::class.java)
            val bundle = Bundle()
            bundle.putFloat("heart_rate", heartRate.toFloat())
            bundle.putFloat("resp_rate", respRate.toFloat())
            intent.putExtras(bundle)
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
        progressBar!!.visibility = View.VISIBLE
        window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
        layoutMainMenu!!.background.alpha = 200
        Log.d(tag, "Camera starting")
//        viewBinding.calculating.visibility = View.VISIBLE

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
                            Toast.makeText(
                                baseContext,
                                "Calculating heart rate ",
                                Toast.LENGTH_LONG
                            ).show()
                            lifecycleScope.launch(Dispatchers.IO) {
                                calculateHeartRate(videoPath)
                                withContext(Dispatchers.Main) {
                                    viewBinding.heartRateVal.setText(heartRate)
                                    Log.i(tag, "Heart rate: $heartRate")
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Heart rate calculated!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    progressBar!!.visibility = View.INVISIBLE
//                                    viewBinding.calculating.visibility = View.INVISIBLE
                                    window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                                    layoutMainMenu!!.background.alpha = 0
                                    viewBinding.measureHeartRate.apply {
                                        text = getString(R.string.capture_heart_rate)
                                        isEnabled = true
                                    }

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