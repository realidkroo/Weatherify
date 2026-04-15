package com.app.weather.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class WeatherType(val title: String) {
    Thunderstorm("Stormy"), Drizzle("Drizzle"), Rain("Rainy"),
    Snow("Snowy"), Atmosphere("Haze"), Clear("Clear"), Clouds("Cloudy")
}

data class ForecastItem(val time: String, val temp: String, val type: WeatherType)
data class DailyForecastItem(val day: String, val temp: String, val tempMin: String = "__", val type: WeatherType, val rainMm: Double = 0.0, val pop: Int = 0)
data class AirPollutionData(val no2: Double = 0.0, val o3: Double = 0.0, val pm10: Double = 0.0, val pm2_5: Double = 0.0, val co: Double = 0.0, val so2: Double = 0.0)
data class WeatherAlert(val title: String, val provider: String, val description: String)

data class WeatherData(
    val type: WeatherType, val temp: Int?, val feelsLike: Int?,
    val location: String, val description: String,
    val aqi: String, val aqiValue: Int, val airPollution: AirPollutionData?,
    val wind: String, val windDeg: Int, val windGust: String,
    val visibility: String, val visibilityM: Int,
    val humidity: String, val humidityValue: Int,
    val rainProb: String, val precipProbMax: Int,
    val pressure: Int, val uvIndex: Double?,
    val rainfallLast24h: Double?, val rainfallNext24h: Double?,
    val sunriseEpoch: Long, val sunsetEpoch: Long, val moonPhase: Double,
    val lat: Double, val lon: Double,
    val feelsLikeDesc: String, val lastUpdated: String,
    val hourlyForecast: List<ForecastItem>, val dailyForecast: List<DailyForecastItem>,
    val activeAlert: WeatherAlert?, val minutelyRain: List<Double>
) {
    companion object {
        val Default = WeatherData(
            type = WeatherType.Clouds, temp = 26, feelsLike = 31,
            location = "Yogyakarta", description = "Clouds",
            aqi = "Good AQI", aqiValue = 1, airPollution = null,
            wind = "1 km/h", windDeg = 0, windGust = "3 km/h",
            visibility = "10 km", visibilityM = 10000,
            humidity = "99%", humidityValue = 99,
            rainProb = "High", precipProbMax = 90, pressure = 1010, uvIndex = 12.4,
            rainfallLast24h = 16.1, rainfallNext24h = 16.1,
            sunriseEpoch = System.currentTimeMillis() / 1000 - 36000, 
            sunsetEpoch = System.currentTimeMillis() / 1000 + 36000,
            moonPhase = 0.5, lat = -7.7956, lon = 110.3695,
            feelsLikeDesc = "Humidity is making it feel warmer.", lastUpdated = "12:00 PM",
            hourlyForecast = List(8) { ForecastItem("12 PM", "26°", WeatherType.Clouds) },
            dailyForecast = List(10) { DailyForecastItem("Today", "30°", "24°", WeatherType.Rain, 16.1, 90) },
            activeAlert = null, minutelyRain = List(60) { 1.5 }
        )
    }
}

object WeatherBackend {
    private var currentProvider = "OpenWeather"
    private var customApiEnabled = false
    private var customApiProvider = ""
    private var customApiKey = ""

    fun setProvider(provider: String) { currentProvider = provider }
    
    fun setCustomApi(enabled: Boolean, provider: String, key: String) {
        customApiEnabled = enabled
        customApiProvider = provider
        customApiKey = key
    }

    suspend fun fetchWeather(city: String): WeatherData = withContext(Dispatchers.IO) {
        // Here you would put your actual HTTP requests using customApiKey if enabled
        // For now, if no key is configured, we safely return the beautiful default UI
        return@withContext WeatherData.Default.copy(location = city)
    }

    suspend fun fetchWeatherByLocation(lat: Double, lon: Double): WeatherData = withContext(Dispatchers.IO) {
        // Real API call goes here. Returning default UI so it doesn't crash while you test
        return@withContext WeatherData.Default.copy(lat = lat, lon = lon)
    }
}

object WeatherCache {
    fun save(context: Context, data: WeatherData) { /* Save to SharedPreferences */ }
    fun load(context: Context): WeatherData? { return null }
}