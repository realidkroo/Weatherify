package com.app.weather.ui

import android.content.Context
import org.json.JSONObject

object SettingsPersistence {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_DATA = "settings_json"

    fun save(context: Context, settings: AppSettings) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = JSONObject().apply {
            put("theme", settings.theme.name)
            put("monet", settings.monet)
            put("haptics", settings.haptics)
            put("blur", settings.blur)
            put("animation", settings.animation)
            put("fx", settings.fx)
            put("quoteStyle", settings.quoteStyle.name)
            put("headerType", settings.headerType.name)
            put("appIcon", settings.appIcon.name)
            put("enableClouds", settings.enableClouds)
            put("debugRotateWindSpeed", settings.debugRotateWindSpeed)
            put("provider", settings.provider)
            put("locationBasedWeather", settings.locationBasedWeather)
            put("demoMode", settings.demoMode)
        }
        prefs.edit().putString(KEY_DATA, json.toString()).apply()
    }

    fun load(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_DATA, null) ?: return AppSettings()

        return try {
            val json = JSONObject(jsonStr)
            AppSettings(
                theme = AppTheme.valueOf(json.optString("theme", AppTheme.Dark.name)),
                monet = json.optBoolean("monet", false),
                haptics = json.optBoolean("haptics", true),
                blur = json.optBoolean("blur", true),
                animation = json.optBoolean("animation", true),
                fx = json.optBoolean("fx", true),
                quoteStyle = QuoteStyle.valueOf(json.optString("quoteStyle", QuoteStyle.Compact.name)),
                headerType = HeaderType.valueOf(json.optString("headerType", HeaderType.Standard.name)),
                appIcon = AppIcon.valueOf(json.optString("appIcon", AppIcon.Day.name)),
                enableClouds = json.optBoolean("enableClouds", false),
                debugRotateWindSpeed = json.optBoolean("debugRotateWindSpeed", false),
                provider = json.optString("provider", "OpenWeather"),
                locationBasedWeather = json.optBoolean("locationBasedWeather", true),
                demoMode = json.optBoolean("demoMode", false)
            )
        } catch (e: Exception) {
            AppSettings()
        }
    }
}
