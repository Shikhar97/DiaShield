package com.example.diashield

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {


//    private lateinit var camera: Camera
//    private lateinit var cameraPreview: SurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(findViewById(R.id.my_toolbar))
        setContentView(R.layout.activity_main)
//        val btnStartRecord = findViewById<Button>(R.id.measure_heart_rate)

//        btnStartRecord.setOnClickListener {
//
//            // Create MediaRecorder
//            mediaRecorder = MediaRecorder()
//
//            // Set video source, output format and encoding
//            mediaRecorder.setCamera(camera)
//            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
//            mediaRecorder.setVideoEncoding(MediaRecorder.VideoEncoder.H264)
//            mediaRecorder.setOutputFile(videoFilePath)
//
//            // Prepare and start recording
//            mediaRecorder.prepare()
//            mediaRecorder.start()
//
//        }


        val buttonStart: Button = findViewById(R.id.button2)

        buttonStart.setOnClickListener {

            val intent = Intent(this, SecondActivity::class.java)
            intent.putExtra("heart_rate", 90.2.toFloat())
            intent.putExtra("resp_rate", 21.2.toFloat())
            startActivity(intent)

        }


    }
}