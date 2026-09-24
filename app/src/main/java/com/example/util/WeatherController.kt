package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherController {

    private const val TAG = "WeatherController"

    // Default coordinates: New Delhi
    private const val DEFAULT_LAT = 28.6139
    private const val DEFAULT_LON = 77.2090

    fun fetchAndSpeakWeather(context: Context, voiceAssistant: VoiceAssistantManager) {
        postToast(context, "🌤️ Fetching Weather Data...")

        val hasFineLoc = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarseLoc = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLoc && !hasCoarseLoc) {
            postToast(context, "⚠️ Location permission missing. Using default city (New Delhi).")
            fetchWeatherForCoordinates(context, voiceAssistant, DEFAULT_LAT, DEFAULT_LON, "New Delhi")
            return
        }

        try {
            val location = getLastKnownLocationFromManager(context)
            if (location != null) {
                fetchWeatherForCoordinates(
                    context,
                    voiceAssistant,
                    location.latitude,
                    location.longitude,
                    "Aapki Current Location"
                )
            } else {
                fetchWeatherForCoordinates(context, voiceAssistant, DEFAULT_LAT, DEFAULT_LON, "New Delhi")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            fetchWeatherForCoordinates(context, voiceAssistant, DEFAULT_LAT, DEFAULT_LON, "New Delhi")
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocationFromManager(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = lm.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l = lm.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                bestLocation = l
            }
        }
        return bestLocation
    }

    private fun fetchWeatherForCoordinates(
        context: Context,
        voiceAssistant: VoiceAssistantManager,
        lat: Double,
        lon: Double,
        locationLabel: String
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val apiUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)
                    val currentWeather = json.getJSONObject("current_weather")

                    val temp = currentWeather.getDouble("temperature")
                    val weatherCode = currentWeather.getInt("weathercode")
                    val windSpeed = currentWeather.optDouble("windspeed", 0.0)

                    val weatherDescHindi = getWeatherCodeDescriptionHindi(weatherCode)

                    val speechText = "Aaj $locationLabel me tapman $temp degree Celsius hai. $weatherDescHindi. Hawa ki raftaar $windSpeed kilometer prati ghanta hai."

                    withContext(Dispatchers.Main) {
                        postToast(context, "🌡️ $temp°C - $weatherDescHindi")
                        voiceAssistant.speak(speechText)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        postToast(context, "❌ Weather fetch error (${connection.responseCode})")
                        voiceAssistant.speak("Mausam ka data fetch nahi ho saka. Kripya net check karein.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch weather from Open-Meteo", e)
                withContext(Dispatchers.Main) {
                    postToast(context, "❌ Weather Error: ${e.localizedMessage}")
                    voiceAssistant.speak("Mausam ki jankari lene me samasya aayi. Net connection check karein.")
                }
            }
        }
    }

    private fun getWeatherCodeDescriptionHindi(code: Int): String {
        return when (code) {
            0 -> "Aasman bilkul saaf hai"
            1, 2, 3 -> "Thode badal chhaye hue hain"
            45, 48 -> "Kohra chhaya hua hai"
            51, 53, 55 -> "Halki boondabaandi ho rahi hai"
            61, 63, 65 -> "Barish ho rahi hai"
            66, 67 -> "Thandi barish ho rahi hai"
            71, 73, 75, 77 -> "Barafbaari ho rahi hai"
            80, 81, 82 -> "Tez barish ke jhatke aa rahe hain"
            85, 86 -> "Tez barafbaari ho rahi hai"
            95, 96, 99 -> "Toofan aur bijli chamakne ke aasaar hain"
            else -> "Mausam saamanya hai"
        }
    }

    private fun postToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
