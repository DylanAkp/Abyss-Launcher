package dev.dylanakp.abysstvlauncher.data

import android.util.Log
import dev.dylanakp.abysstvlauncher.domain.repository.WeatherRepository as IWeatherRepository
import dev.dylanakp.abysstvlauncher.model.TemperatureUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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

@Serializable
private data class GeocodingResponse(
    val results: List<GeocodingResult> = emptyList()
)

@Serializable
private data class GeocodingResult(
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

@Serializable
private data class WeatherResponse(
    val current: CurrentWeather? = null
)

@Serializable
private data class CurrentWeather(
    @SerialName("temperature_2m") val temperature2m: Double = 0.0,
    @SerialName("weather_code") val weatherCode: Int = 0
)

object WeatherRepository : IWeatherRepository, KoinComponent {
    private val client: OkHttpClient by inject()
    private val json: Json by inject()

    private var cachedInfo: WeatherInfo? = null
    private var lastFetchTime = 0L
    private var cachedCity = ""

    override fun clearCache() {
        cachedInfo = null
        lastFetchTime = 0L
        cachedCity = ""
    }

    override suspend fun getWeather(cityName: String): WeatherInfo? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedInfo != null && cachedCity.equals(cityName, ignoreCase = true) && (now - lastFetchTime < 30 * 60 * 1000L)) {
            return@withContext cachedInfo
        }

        try {
            val encCity = URLEncoder.encode(cityName.trim().ifBlank { "Paris" }, "UTF-8")
            val lang = java.util.Locale.getDefault().language.ifEmpty { "en" }
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encCity&count=1&language=$lang&format=json"
            val geoReq = Request.Builder()
                .url(geoUrl)
                .header("User-Agent", "AbyssLauncher/2.0")
                .build()
            val geoResp = client.newCall(geoReq).execute()
            if (!geoResp.isSuccessful) return@withContext cachedInfo
            val geoBody = geoResp.body?.string() ?: return@withContext cachedInfo
            val geoData = json.decodeFromString<GeocodingResponse>(geoBody)
            val first = geoData.results.firstOrNull() ?: return@withContext cachedInfo

            val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=${first.latitude}&longitude=${first.longitude}&current=temperature_2m,weather_code"
            val weatherReq = Request.Builder()
                .url(weatherUrl)
                .header("User-Agent", "AbyssLauncher/2.0")
                .build()
            val weatherResp = client.newCall(weatherReq).execute()
            if (!weatherResp.isSuccessful) return@withContext cachedInfo
            val weatherBody = weatherResp.body?.string() ?: return@withContext cachedInfo
            val weatherData = json.decodeFromString<WeatherResponse>(weatherBody)
            val current = weatherData.current ?: return@withContext cachedInfo

            val info = WeatherInfo(
                temperatureC = current.temperature2m,
                weatherCode = current.weatherCode,
                iconEmoji = weatherCodeToEmoji(current.weatherCode),
                cityName = first.name.ifBlank { cityName }
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

    private fun weatherCodeToEmoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55 -> "🌦️"
        61, 63, 65 -> "🌧️"
        71, 73, 75 -> "🌨️"
        77 -> "❄️"
        80, 81, 82 -> "🌧️"
        85, 86 -> "🌨️"
        95, 96, 99 -> "⛈️"
        else -> "🌤️"
    }
}
