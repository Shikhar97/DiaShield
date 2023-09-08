package com.example.diashield

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RatingBar
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob


class SecondActivity : AppCompatActivity() {
    private val tag = "DiaShield"
    private val applicationScope = CoroutineScope(SupervisorJob())

    // Using by lazy so the database and the repository are only created when they're needed
    // rather than when the application starts
    private val database by lazy { VitalsDb.AppDatabase.getDatabase(this, applicationScope) }
    private val repository by lazy { VitalsRepository(database.userDao()) }


    private val vitalViewModel: VitalViewModel by viewModels {
        VitalViewModelFactory(repository)
    }

    var symptomMap = HashMap<String, Float>()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.second_activity)
        val spinnerSymptoms = findViewById<Spinner>(R.id.spinner_symptoms)
        val symptomsArray = resources.getStringArray(R.array.symptoms_list)
        val rBar = findViewById<RatingBar>(R.id.ratingBar)
        for (symptom in symptomsArray) {
            symptomMap[symptom] = 0f
        }

        rBar.onRatingBarChangeListener = RatingBar.OnRatingBarChangeListener { _, rating, _ ->

            val selectedItem = spinnerSymptoms.selectedItem as String
            symptomMap[selectedItem] = rating
            Toast.makeText(
                this@SecondActivity,
                "You selected $rating for $selectedItem",
                Toast.LENGTH_SHORT
            ).show()
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, symptomsArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        spinnerSymptoms.adapter = adapter
        spinnerSymptoms.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedItem = parent?.selectedItem.toString()
                rBar.stepSize = 1.0.toFloat()
                rBar.numStars = 5
                rBar.rating = symptomMap[selectedItem]!!
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                TODO("Not yet implemented")
            }
        }


        val upload: FloatingActionButton = findViewById(R.id.upload_button)
        upload.setOnClickListener {
            val bundle = intent.extras

            val dataToInsert = VitalsDb.VitalsUser(
                heartRate = bundle?.getFloat("heart_rate"),
                respRate = bundle?.getFloat("resp_rate"),
                nausea = symptomMap["Nausea"],
                headache = symptomMap["Headache"],
                diarrhea = symptomMap["Diarrhea"],
                soarThroat = symptomMap["Soar Throat"],
                fever = symptomMap["Fever"],
                muscleAche = symptomMap["Muscle Ache"],
                lossOfSmellTaste = symptomMap["Loss of Smell/Taste"],
                cough = symptomMap["Cough"],
                breathlessness = symptomMap["Breathlessness"],
                feelingTired = symptomMap["Feeling tired"]
            )
            vitalViewModel.insert(dataToInsert)
            Log.d("CameraXApp","Updated in database")
            for ((key, value) in symptomMap) {
                Log.d("YourTag","$key=$value")
            }

            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
    }
}
