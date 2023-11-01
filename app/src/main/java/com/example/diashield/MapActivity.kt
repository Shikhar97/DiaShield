package com.example.diashield

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.diashield.databinding.ActivityMapBinding
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Collections
import kotlin.math.roundToInt


class MapActivity : AppCompatActivity() {

    private var tag = "DiaShield"
    private val gson = Gson()
    private var routesArray = JsonObject()
    private var avgSpeed = 0.0
    private fun getDirections(src: String, dest: String): JsonObject {
        val url =
            "https://maps.googleapis.com/maps/api/directions/json?origin=$src&destination=$dest&key="
        val client = OkHttpClient()
        lifecycleScope.launch(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .build()
            val response = client.newCall(request).execute()
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val responseBody = response.body
                    val responseText = responseBody?.string()
                    val jsonObject = gson.fromJson(responseText, JsonObject::class.java)
                    routesArray = jsonObject.getAsJsonArray("routes")[0].asJsonObject
                        .getAsJsonArray("legs")[0].asJsonObject
                    avgSpeed =
                        (routesArray.get("distance").asJsonObject.get("value").asDouble / routesArray.get(
                            "duration"
                        ).asJsonObject.get("value").asDouble *  2.23694 * 100.0).roundToInt() / 100.0
                    Log.i(tag, "Avg Speed: $avgSpeed")
                    val currentSpeed = sampleTrafficCongestion(routesArray)
                    val labels = categorizeRoadCondition(avgSpeed, currentSpeed)
                    Log.i(tag, "Road Conditions: $labels")
//                    val speedDiff = avgSpeed - currentSpeed
                    Toast.makeText(
                        baseContext,
                        "Road Conditions: $labels, Avg. Speed: ${avgSpeed}, Current Speed: $currentSpeed",
                        Toast.LENGTH_SHORT
                    ).show()

                } else {
                    Log.i(tag, "Request failed with code: ${response.code}")
                }
                response.close()
            }

        }
        return routesArray
    }

    private fun categorizeRoadCondition(avg: Double, current: Double): String {
        return if(avg - current > 0){
            "HCW"
        } else {
            "LCW"
        }

    }

    private suspend fun sampleTrafficCongestion(
        directions: JsonObject,
    ): Double {
        var currentSpeed = 0.0
//        val steps = directions.getAsJsonArray("steps")
        var congestion = mutableListOf<Job>()

        runBlocking {
            val startLocation = directions.get("start_location").asJsonObject
            val endLocation = directions.get("end_location").asJsonObject

            val client = OkHttpClient()

            val startLat = startLocation.get("lat").asDouble
            val startLng = startLocation.get("lng").asDouble
            val endLat = endLocation.get("lat").asDouble
            val endLng = endLocation.get("lng").asDouble

            val job = launch(context = Dispatchers.Default) {
                coroutineScope {
                    val matrixUrl =
                        "https://maps.googleapis.com/maps/api/distancematrix/json?traffic_model=best_guess&departure_time=now&destinations=$startLat,$startLng&origins=$endLat,$endLng&mode=driving&key="

                    val request = Request.Builder()
                        .url(matrixUrl)
                        .build()
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body
                        val responseText = responseBody?.string()
                        val jsonObject = gson.fromJson(responseText, JsonObject::class.java)
                        val duration = jsonObject.getAsJsonArray("rows")[0].asJsonObject
                            .getAsJsonArray("elements")[0].asJsonObject
                            .get("duration_in_traffic").asJsonObject
                            .get("value").asDouble
                        val distance = jsonObject.getAsJsonArray("rows")[0].asJsonObject
                            .getAsJsonArray("elements")[0].asJsonObject
                            .get("distance").asJsonObject
                            .get("value").asDouble
                        currentSpeed = ((distance / duration)  *  2.23694 * 100.0).roundToInt() / 100.0
                    } else {
                        Log.i(tag, "Request failed with code: ${response.code}")
                    }
                    response.close()
                }

            }
            congestion.add(job)
        }
        congestion = Collections.unmodifiableList(congestion)
        congestion.joinAll()
        Log.i(tag, "Current Speed: $currentSpeed")
        return currentSpeed
    }

    private lateinit var mapBinding: ActivityMapBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apiVersion = android.os.Build.VERSION.SDK_INT

        mapBinding = ActivityMapBinding.inflate(layoutInflater)
        setContentView(mapBinding.root)
        Log.i(tag, "Android API Version: $apiVersion")
        val extendedFab: Button = findViewById(R.id.get_road_con)

        val sourceTextInput = findViewById<TextInputLayout>(R.id.source_parent)
        val destinationTextInput = findViewById<TextInputLayout>(R.id.destination_parent)

        extendedFab.setOnClickListener {
            val source = sourceTextInput.editText?.text.toString()
            val destination = destinationTextInput.editText?.text.toString()
            getDirections(source, destination)
        }

    }

}