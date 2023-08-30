package com.example.diashield

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton


class SecondActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        setSupportActionBar(findViewById(R.id.my_toolbar))
        setContentView(R.layout.second_activity);
        val spinnerSymptoms = findViewById<Spinner>(R.id.spinner_symptoms);

        val symptomsArray = resources.getStringArray(R.array.symptoms_list);
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, symptomsArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSymptoms.adapter = adapter
        val upload: FloatingActionButton = findViewById(R.id.upload_button)

        upload.setOnClickListener {

            onBackPressed()

        }
    }
        override fun onBackPressed() {

            // Optional - Save data, persist state

            // Close activity
            super.onBackPressed()
            finish()

            // Launch MainActivity
            val intent = Intent(this, MainActivity::class.java);
            startActivity(intent);

        }

    }