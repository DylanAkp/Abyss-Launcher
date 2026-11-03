package dev.dylanakp.abysstvlauncher.data

import android.util.Log
import dev.dylanakp.abysstvlauncher.model.TemperatureUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class WeatherInfo(
    val temperatureC: Double,
    val weatherCode: Int,
    val iconEmoji: String,
    val cityName: String
) {
    fun displayString(unit: TemperatureUnit): String {
        val temp = if (unit == TemperatureUnit.FAHRENHEIT) {
            (temperatureC * 9.0 / 5.0 + 32.0).roundToInt()
        } else {
            temperatureC.roundToInt()
        }
        return "$iconEmoji  $temp${unit.symbol}"
    }
}

object WeatherRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var cachedInfo: WeatherInfo? = null
    private var lastFetchTime = 0L
    private var cachedCity = ""

    fun clearCache() {
        cachedInfo = null
        lastFetchTime = 0L
        cachedCity = ""
    }

    suspend fun getWeather(cityName: String): WeatherInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedInfo != null && cachedCity.equals(cityName, ignoreCase = true) && (now - lastFetchTime < 30 * 60 * 1000L)) {
            return@withContext cachedInfo
        }

        try {
            val encCity = URLEncoder.encode(cityName.trim().ifBlank { "Paris" }, "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encCity&count=1&language=fr&format=json"
            val geoReq = Request.Builder()
                .url(geoUrl)
                .header("User-Agent", "AbyssLauncher/2.0")
                .build()
            val geoResp = client.newCall(geoReq).execute()
            if (!geoResp.isSuccessful) return@withContext cachedInfo
            val geoJson = JSONObject(geoResp.body?.string() ?: return@withContext cachedInfo)
            val results = geoJson.optJSONArray("results") ?: return@withContext cachedInfo
            if (results.length() == 0) return@withContext cachedInfo

            val first = results.getJSONObject(0)
            val lat = first.getDouble("latitude")
            val lon = first.getDouble("longitude")
            val resolvedName = first.optString("name", cityName)

            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
            val weatherReq = Request.Builder()
                .url(weatherUrl)
                .header("User-Agent", "AbyssLauncher/2.0")
                .build()
            val weatherResp = client.newCall(weatherReq).execute()
            if (!weatherResp.isSuccessful) return@withContext cachedInfo
            val weatherJson = JSONObject(weatherResp.body?.string() ?: return@withContext cachedInfo)
            val current = weatherJson.optJSONObject("current") ?: return@withContext cachedInfo

            val temp = current.getDouble("temperature_2m")
            val code = current.getInt("weather_code")
            val emoji = weatherCodeToEmoji(code)

            val info = WeatherInfo(
                temperatureC = temp,
                weatherCode = code,
                iconEmoji = emoji,
                cityName = resolvedName
            )
            cachedInfo = info
            cachedCity = cityName
            lastFetchTime = now
            info
        } catch (e: Exception) {
            Log.w("AbyssWeather", "Weather fetch error: ${e.message}")
            cachedInfo
        }
    }

    private fun weatherCodeToEmoji(code: Int): String {
        return when (code) {
            0 -> "☀️"
            1, 2 -> "🌤️"
            3 -> "☁️"
            45, 48 -> "🌫️"
            51, 53, 55, 56, 57 -> "🌦️"
            61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
            71, 73, 75, 77, 85, 86 -> "🌨️"
            95, 96, 99 -> "⛈️"
            else -> "⛅"
        }
    }
}
