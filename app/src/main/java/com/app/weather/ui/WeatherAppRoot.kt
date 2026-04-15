package com.app.weather.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class Destination { Weather, Search, Settings } 
enum class AppTheme { Light, Dark, Auto }
enum class QuoteStyle { Compact, Summary }
enum class HeaderType { Greeting, FeelsLike, Sunrise, Disabled, Standard }
enum class AppIcon { Day, NightFullMoon, NightMoon }
enum class OverlayType { None, Theme, Quote, Header, Icons, Permissions, Credits, Provider, ApiKeys }
enum class NestedOverlay { None, HeaderTypeSelection }
enum class NavType { Tab, Push, Pop, Instant }

data class AppSettings(
    val theme:        AppTheme   = AppTheme.Dark,
    val haptics:      Boolean    = true,
    val blur:         Boolean    = true,
    val animation:    Boolean    = true,
    val fx:           Boolean    = true,
    val quoteStyle:   QuoteStyle = QuoteStyle.Compact,
    val headerType:   HeaderType = HeaderType.Standard,
    val appIcon:      AppIcon    = AppIcon.Day,
    val enableClouds: Boolean    = false,
    val debugRotateWindSpeed: Boolean = false,
    val provider:     String     = "OpenWeather",
    val customApiEnabled: Boolean = false,
    val customApiProvider: String = "Select here",
    val customApiKey: String = ""
)

object SettingsStore {
    private const val PREFS_NAME = "weatherify_settings"

    fun save(context: Context, settings: AppSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putString("theme", settings.theme.name)
            putBoolean("haptics", settings.haptics)
            putBoolean("blur", settings.blur)
            putBoolean("animation", settings.animation)
            putBoolean("fx", settings.fx)
            putString("quoteStyle", settings.quoteStyle.name)
            putString("headerType", settings.headerType.name)
            putString("appIcon", settings.appIcon.name)
            putBoolean("enableClouds", settings.enableClouds)
            putBoolean("debugRotateWindSpeed", settings.debugRotateWindSpeed)
            putString("provider", settings.provider)
            putBoolean("customApiEnabled", settings.customApiEnabled)
            putString("customApiProvider", settings.customApiProvider)
            putString("customApiKey", settings.customApiKey)
        }.apply()
    }

    fun load(context: Context): AppSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppSettings(
            theme = AppTheme.valueOf(prefs.getString("theme", AppTheme.Dark.name) ?: AppTheme.Dark.name),
            haptics = prefs.getBoolean("haptics", true),
            blur = prefs.getBoolean("blur", true),
            animation = prefs.getBoolean("animation", true),
            fx = prefs.getBoolean("fx", true),
            quoteStyle = QuoteStyle.valueOf(prefs.getString("quoteStyle", QuoteStyle.Compact.name) ?: QuoteStyle.Compact.name),
            headerType = HeaderType.valueOf(prefs.getString("headerType", HeaderType.Standard.name) ?: HeaderType.Standard.name),
            appIcon = AppIcon.valueOf(prefs.getString("appIcon", AppIcon.Day.name) ?: AppIcon.Day.name),
            enableClouds = prefs.getBoolean("enableClouds", false),
            debugRotateWindSpeed = prefs.getBoolean("debugRotateWindSpeed", false),
            provider = prefs.getString("provider", "OpenWeather") ?: "OpenWeather",
            customApiEnabled = prefs.getBoolean("customApiEnabled", false),
            customApiProvider = prefs.getString("customApiProvider", "Select here") ?: "Select here",
            customApiKey = prefs.getString("customApiKey", "") ?: ""
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
fun WeatherAppRoot() {
    val context      = LocalContext.current
    val activity     = context as? ComponentActivity
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    var backStack    by remember { mutableStateOf(listOf(Destination.Weather)) }
    var forwardStack by remember { mutableStateOf(listOf<Destination>()) }
    val currentDestination = backStack.last()
    var navType      by remember { mutableStateOf(NavType.Tab) }

    var settingsMenu by remember { mutableStateOf("Main") }
    var weatherData  by remember { mutableStateOf(WeatherCache.load(context) ?: WeatherData.Default) }
    var settings     by remember { mutableStateOf(SettingsStore.load(context)) }

    LaunchedEffect(settings) {
        SettingsStore.save(context, settings)
        WeatherBackend.setProvider(settings.provider)
        WeatherBackend.setCustomApi(settings.customApiEnabled, settings.customApiProvider, settings.customApiKey)
    }

    var activeOverlay    by remember { mutableStateOf(OverlayType.None) }
    var displayedOverlay by remember { mutableStateOf(OverlayType.None) }
    val overlayProgress  = remember { Animatable(0f) }
    var primaryOverlayHeightPx by remember { mutableStateOf(0f) }

    var activeNestedOverlay by remember { mutableStateOf(NestedOverlay.None) }
    val stackedOverlayProgress = remember { Animatable(0f) }
    var secondaryOverlayHeightPx by remember { mutableStateOf(0f) }

    val swipeOffset   = remember { Animatable(0f) }
    var swipeBgDest   by remember { mutableStateOf<Destination?>(null) }
    val glassState = remember { GlassState() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun loadWeatherForLocation(lat: Double?, lon: Double?) {
        coroutineScope.launch {
            val res = if (lat != null && lon != null) WeatherBackend.fetchWeatherByLocation(lat, lon)
                      else WeatherBackend.fetchWeather("Jakarta")
            weatherData = res
            WeatherCache.save(context, res)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).addOnSuccessListener { loc ->
                if (loc != null) loadWeatherForLocation(loc.latitude, loc.longitude)
                else loadWeatherForLocation(null, null)
            }.addOnFailureListener { loadWeatherForLocation(null, null) }
        } else loadWeatherForLocation(null, null)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).addOnSuccessListener { loc ->
                if (loc != null) loadWeatherForLocation(loc.latitude, loc.longitude)
                else loadWeatherForLocation(null, null)
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val dynamicBarBg by animateColorAsState(
        targetValue = if (currentDestination != Destination.Weather) Color.Black.copy(alpha = 0.45f)
                      else when (weatherData.type) {
                          WeatherType.Clear, WeatherType.Clouds, WeatherType.Snow -> Color.Black.copy(alpha = 0.15f)
                          else -> Color.White.copy(alpha = 0.12f)
                      }, animationSpec = tween(500), label = ""
    )

    fun handleBack(requestedNavType: NavType = NavType.Pop) {
        if (activeNestedOverlay != NestedOverlay.None) activeNestedOverlay = NestedOverlay.None
        else if (activeOverlay != OverlayType.None) activeOverlay = OverlayType.None
        else if (currentDestination == Destination.Settings && settingsMenu != "Main") { /* Handled in SettingsScreen */ } 
        else if (backStack.size > 1) {
            navType = requestedNavType
            forwardStack = listOf(backStack.last()) + forwardStack
            backStack = backStack.dropLast(1)
        } else activity?.finish()
    }

    BackHandler(enabled = backStack.size > 1 || activeOverlay != OverlayType.None) { handleBack(NavType.Pop) }

    LaunchedEffect(activeOverlay, activeNestedOverlay) {
        when {
            activeNestedOverlay != NestedOverlay.None -> stackedOverlayProgress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 400f))
            activeOverlay != OverlayType.None -> {
                if (stackedOverlayProgress.value > 0f) stackedOverlayProgress.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = 500f))
                displayedOverlay = activeOverlay
                overlayProgress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 300f))
            }
            else -> {
                launch { stackedOverlayProgress.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = 500f)) }
                overlayProgress.animateTo(0f, spring(dampingRatio = 0.95f, stiffness = 400f))
                displayedOverlay = OverlayType.None
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1f - 0.08f * overlayProgress.value - 0.05f * stackedOverlayProgress.value
                scaleY = 1f - 0.08f * overlayProgress.value - 0.05f * stackedOverlayProgress.value
            }.clip(RoundedCornerShape((32f * overlayProgress.value + 16f * stackedOverlayProgress.value).dp))
             .blur((12f * overlayProgress.value + 8f * stackedOverlayProgress.value).dp, BlurredEdgeTreatment.Unbounded)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D))) {
                AnimatedContent(targetState = currentDestination, label = "AppNavigation") { destination ->
                    when (destination) {
                        Destination.Weather  -> MainWeatherScreen(data = weatherData, settings = settings)
                        Destination.Search   -> SearchScreen(onBack = { handleBack(NavType.Pop) })
                        Destination.Settings -> SettingsScreen(
                            settings = settings, currentMenu = settingsMenu,
                            onSelectWeather = { weatherData = weatherData.copy(type = it, description = "forced state") },
                            onMenuChange = { settingsMenu = it },
                            onUpdateSettings = { settings = it },
                            onOpenOverlay = { activeOverlay = it },
                            onBack = { handleBack(NavType.Pop) }
                        )
                    }
                }
            }
        
            LiquidGlassNavBar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
                barBackground = dynamicBarBg, weatherType = weatherData.type,
                activeDestination = currentDestination, glassState = glassState,
                onWeatherCycle = {},
                onNavigate = { dest ->
                    if (dest != currentDestination) {
                        navType = NavType.Tab
                        forwardStack = emptyList()
                        settingsMenu = "Main"
                        backStack = if (dest == Destination.Weather) listOf(Destination.Weather) else backStack + dest
                    }
                }
            )
        }

        if (stackedOverlayProgress.value > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f * stackedOverlayProgress.value)))

        if (displayedOverlay != OverlayType.None || overlayProgress.value > 0f) {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = overlayProgress.value * 0.5f }.background(Color.Black).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { handleBack() })
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).onGloballyPositioned { primaryOverlayHeightPx = it.size.height.toFloat() }
                    .graphicsLayer {
                        scaleX = 1f - 0.06f * stackedOverlayProgress.value
                        scaleY = 1f - 0.06f * stackedOverlayProgress.value
                        translationY = ((1f - overlayProgress.value) * primaryOverlayHeightPx)
                        alpha = 1f - 0.2f * stackedOverlayProgress.value
                    }.blur((8f * stackedOverlayProgress.value).dp, BlurredEdgeTreatment.Unbounded)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (overlayProgress.value < 0.8f) handleBack()
                                else coroutineScope.launch { overlayProgress.animateTo(1f, spring(stiffness = 400f)) }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch { overlayProgress.snapTo((overlayProgress.value - (dragAmount / (primaryOverlayHeightPx.takeIf { it > 0 } ?: 2000f))).coerceIn(0f, 1f)) }
                            }
                        )
                    }
            ) {
                OverlayContent(overlayType = displayedOverlay, settings = settings, onUpdateSettings = { settings = it }, onOpenNested = { activeNestedOverlay = it })
            }
        }
    }
}