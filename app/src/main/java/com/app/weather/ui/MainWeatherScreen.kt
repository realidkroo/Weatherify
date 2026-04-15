package com.app.weather.ui

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import android.os.Build
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.isActive
import org.intellij.lang.annotations.Language
import kotlin.math.*

// ─── Shaders ─────────────────────────────────────────────────────────────────

@Language("AGSL")
private const val SKY_SHADER = """
    uniform float scrollOffset;
    uniform float iResolutionX;
    uniform float iResolutionY;
    layout(color) uniform half4 skyTop;
    layout(color) uniform half4 skyBottom;
    half4 main(in float2 fragCoord) {
        float2 uv = fragCoord.xy / float2(iResolutionX, iResolutionY);
        float parallaxShift = scrollOffset * 0.2;
        vec4 bg = mix(skyTop, skyBottom, uv.y + 0.2 + parallaxShift);
        return half4(bg.r, bg.g, bg.b, 1.0);
    }
"""

@Language("AGSL")
private const val CLOUD_SHADER = """
    uniform float iTime;
    uniform float scrollOffset;
    uniform float iResolutionX;
    uniform float iResolutionY;
    layout(color) uniform half4 cloudColor;
    uniform float cloudDensityMult;
    uniform float windSpeedMult;
    uniform float layerSeed;
    uniform float layerAlphaMult;
    uniform float isFog;

    float hash(float2 p) {
        float3 p3 = fract(float3(p.xyx) * float3(0.1031, 0.1030, 0.0973));
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    float noise(float2 x) {
        float2 i = floor(x);
        float2 f = fract(x);
        float2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
        return mix(
            mix(hash(i + float2(0.0, 0.0)), hash(i + float2(1.0, 0.0)), u.x),
            mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x),
            u.y
        );
    }

    const float2x2 m = float2x2(0.8, -0.6, 0.6, 0.8);

    float fbm(float2 p) {
        float f = 0.0;
        float a = 0.5;
        for (int i = 0; i < 6; i++) {
            f += a * noise(p);
            p = m * p * 2.0;
            a *= 0.5;
        }
        return f;
    }

    half4 main(in float2 fragCoord) {
        float2 uv = fragCoord / float2(iResolutionX, iResolutionY);
        float aspect = iResolutionX / iResolutionY;
        uv.y -= scrollOffset * 0.5;
        float2 cuv = float2(uv.x * aspect, uv.y);
        float2 p = cuv * 3.0 + layerSeed;
        p.x += iTime * 0.012 * windSpeedMult;
        float w = fbm(p * 0.8);
        float n = fbm(p + float2(w, w) * 1.5);
        float vMask = 1.0;
        if (isFog > 0.5) {
            vMask = smoothstep(0.1, 1.0, uv.y + n * 0.2);
            vMask *= smoothstep(0.0, 0.3, uv.y);
        } else {
            float center = 0.25;
            float spread = 0.3;
            float vDist = uv.y - center;
            vMask = exp(-(vDist * vDist) / (2.0 * spread * spread));
            vMask *= smoothstep(0.65, 0.3, uv.y);
            vMask *= smoothstep(-0.1, 0.1, uv.y);
        }
        float density = 0.0;
        if (isFog > 0.5) {
            density = smoothstep(0.2, 0.9, n) * vMask * cloudDensityMult;
        } else {
            density = smoothstep(0.35, 0.75, n) * vMask * clamp(cloudDensityMult, 0.0, 1.5);
        }
        density = clamp(density, 0.0, 1.0);
        float2 lightDir = float2(-0.05, -0.05);
        float ln = fbm(p + lightDir + float2(w, w) * 1.5);
        float lDensity = 0.0;
        if (isFog > 0.5) {
            lDensity = smoothstep(0.2, 0.9, ln) * vMask * cloudDensityMult;
        } else {
            lDensity = smoothstep(0.35, 0.75, ln) * vMask * clamp(cloudDensityMult, 0.0, 1.5);
        }
        float shadow = clamp(density - lDensity, 0.0, 1.0);
        float highlight = clamp(lDensity - density, 0.0, 1.0);
        vec3 col = cloudColor.rgb;
        if (isFog > 0.5) {
            col *= mix(1.0, 0.8, shadow);
            col += highlight * 0.15;
        } else {
            col *= mix(1.0, 0.3, shadow * 2.5);
            col += highlight * 0.5;
        }
        return half4(col.r, col.g, col.b, density * layerAlphaMult);
    }
"""

// ─── Widget helpers ───────────────────────────────────────────────────────────

@Composable
private fun WidgetTile(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(16.dp)
    ) { Column(modifier = Modifier.fillMaxSize(), content = content) }
}

@Composable
private fun WidgetLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun FullWidgetBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(16.dp)
    ) {
        Column {
            WidgetLabel(icon, label)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ─── Main Screen ──────────────────────────────────────────────────────────────

@SuppressLint("SetJavaScriptEnabled")
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun MainWeatherScreen(data: WeatherData, settings: AppSettings) {
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    val maxScroll = 6000f
    val density = LocalDensity.current
    val excessScroll = (scrollOffset - 800f).coerceAtLeast(0f)

    val scrollableState = rememberScrollableState { delta ->
        scrollOffset = (scrollOffset - delta).coerceIn(0f, maxScroll)
        delta
    }

    val headerProgress = (scrollOffset / 800f).coerceIn(0f, 1f)
    val smoothProgress by animateFloatAsState(targetValue = headerProgress, animationSpec = spring(dampingRatio = 0.75f, stiffness = 200f), label = "")

    val titleSize    = (18f + 10f * smoothProgress).sp
    val tempSize     = (130f - 102f * smoothProgress).sp
    val tempAlpha    = (1f - 0.3f * smoothProgress).coerceIn(0f, 1f)
    val titleWeight  = if (smoothProgress > 0.5f) FontWeight.Bold else FontWeight.SemiBold
    val titleY       = (130f - 70f * smoothProgress).dp
    val titleX       = 32.dp
    val tempY        = (175f - 115f * smoothProgress).dp

    val headerText = when (settings.headerType) {
        HeaderType.Greeting  -> data.type.title
        HeaderType.Standard  -> data.location
        HeaderType.Sunrise   -> if (data.sunriseEpoch != null) "Sunrise at ${epochToTimeStr(data.sunriseEpoch)}" else "Sunrise --"
        HeaderType.FeelsLike -> "Feels like ${data.feelsLike ?: "__"}°"
        HeaderType.Disabled  -> ""
    }

    val textMeasurer = rememberTextMeasurer()
    val titleTextWidthPx = textMeasurer.measure(text = headerText, style = TextStyle(fontSize = titleSize, fontWeight = titleWeight)).size.width
    val exactTitleWidthDp = with(LocalDensity.current) { titleTextWidthPx.toDp() }
    val targetDockedX = 32.dp + exactTitleWidthDp + 22.dp
    val tempX   = 32.dp + (targetDockedX - 32.dp) * smoothProgress
    val dashX   = 32.dp + exactTitleWidthDp + 8.dp
    val dashAlpha = if (settings.headerType == HeaderType.Disabled) 0f else smoothProgress.coerceIn(0f, 1f)

    val contentBlockY     = (345f - 220f * smoothProgress).dp
    val contentBlockAlpha = (1f - smoothProgress * 1.5f).coerceIn(0f, 1f)
    val contentBlockBlur  = (20f * smoothProgress.coerceAtLeast(0f)).dp

    // Sky colors
    val skyTopColor by animateColorAsState(when (data.type) {
        WeatherType.Clear        -> Color(0xFF4A90E2)
        WeatherType.Clouds       -> Color(0xFF6A8296)
        WeatherType.Rain, WeatherType.Drizzle -> Color(0xFF2C3E50)
        WeatherType.Thunderstorm -> Color(0xFF13141C)
        WeatherType.Atmosphere   -> Color(0xFF90A4AE)
        WeatherType.Snow         -> Color(0xFFB0BEC5)
    }, animationSpec = tween(1500), label = "")

    val skyBottomColor by animateColorAsState(when (data.type) {
        WeatherType.Clear        -> Color(0xFF87CEEB)
        WeatherType.Clouds       -> Color(0xFF90A4AE)
        WeatherType.Rain, WeatherType.Drizzle -> Color(0xFF546E7A)
        WeatherType.Thunderstorm -> Color(0xFF2E323E)
        WeatherType.Atmosphere   -> Color(0xFFCFD8DC)
        WeatherType.Snow         -> Color(0xFFECEFF1)
    }, animationSpec = tween(1500), label = "")

    val cloudColor by animateColorAsState(when (data.type) {
        WeatherType.Clear        -> Color(0xFFFFFFFF)
        WeatherType.Clouds       -> Color(0xFFF0F0F0)
        WeatherType.Rain, WeatherType.Drizzle -> Color(0xFF78909C)
        WeatherType.Thunderstorm -> Color(0xFF37474F)
        WeatherType.Atmosphere   -> Color(0xFFBCAAA4)
        WeatherType.Snow         -> Color(0xFFECEFF1)
    }, animationSpec = tween(1500), label = "")

    val cloudDensityMult by animateFloatAsState(when (data.type) {
        WeatherType.Clear        -> 0.0f
        WeatherType.Clouds       -> 1.0f
        WeatherType.Rain, WeatherType.Drizzle -> 1.2f
        WeatherType.Thunderstorm -> 1.5f
        WeatherType.Atmosphere   -> 1.2f
        WeatherType.Snow         -> 1.0f
    }, animationSpec = tween(1500), label = "")

    val isFogAnim by animateFloatAsState(
        targetValue = if (data.type == WeatherType.Atmosphere) 1f else 0f,
        animationSpec = tween(1500), label = ""
    )

    val windSpeedMult by animateFloatAsState(when (data.type) {
        WeatherType.Thunderstorm -> 2.5f
        WeatherType.Rain         -> 1.5f
        WeatherType.Clear        -> 0.5f
        else                     -> 1.0f
    }, animationSpec = tween(1500), label = "")

    val skyShader       = remember { RuntimeShader(SKY_SHADER) }
    val cloudBackShader = remember { RuntimeShader(CLOUD_SHADER) }
    val cloudFrontShader = remember { RuntimeShader(CLOUD_SHADER) }
    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (isActive) { withInfiniteAnimationFrameMillis { frameTime -> time = (frameTime % 100_000L) / 1000f } }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)
        .scrollable(state = scrollableState, orientation = Orientation.Vertical)) {

        // ── Sky background ─────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxSize().drawWithCache {
            skyShader.setFloatUniform("iResolutionX", size.width)
            skyShader.setFloatUniform("iResolutionY", size.height)
            skyShader.setFloatUniform("scrollOffset", scrollOffset / size.height)
            skyShader.setColorUniform("skyTop", skyTopColor.toArgb())
            skyShader.setColorUniform("skyBottom", skyBottomColor.toArgb())
            onDrawBehind { drawRect(brush = ShaderBrush(skyShader)) }
        })

        if (settings.enableClouds) {
            Box(modifier = Modifier.fillMaxSize().drawWithCache {
                cloudBackShader.setFloatUniform("iResolutionX", size.width)
                cloudBackShader.setFloatUniform("iResolutionY", size.height)
                cloudBackShader.setFloatUniform("iTime", time)
                cloudBackShader.setFloatUniform("scrollOffset", scrollOffset / size.height)
                cloudBackShader.setColorUniform("cloudColor", cloudColor.toArgb())
                cloudBackShader.setFloatUniform("cloudDensityMult", cloudDensityMult)
                cloudBackShader.setFloatUniform("windSpeedMult", windSpeedMult)
                cloudBackShader.setFloatUniform("layerSeed", 0f)
                cloudBackShader.setFloatUniform("layerAlphaMult", 1f)
                cloudBackShader.setFloatUniform("isFog", isFogAnim)
                onDrawBehind { drawRect(brush = ShaderBrush(cloudBackShader)) }
            })
        }

        // ── Header & widgets ───────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().height(8000.dp)) {

            // Quote / description block
            val dynamicQuote = if (settings.quoteStyle == QuoteStyle.Compact) {
                if (data.type == WeatherType.Rain || data.type == WeatherType.Thunderstorm || data.type == WeatherType.Drizzle) {
                    "It feels like ${data.feelsLike ?: "__"}°C today,\nRaining until later. Beware of flood."
                } else {
                    "It feels like ${data.feelsLike ?: "__"}°C today,\nProbability of rain is ${data.rainProb}."
                }
            } else {
                val mainEvent = when (data.type) {
                    WeatherType.Clear        -> "It's completely clear."
                    WeatherType.Atmosphere   -> "Visibility is extremely low right now."
                    WeatherType.Rain         -> "It's heavily raining."
                    WeatherType.Drizzle      -> "It's light raining now."
                    WeatherType.Thunderstorm -> "Intense storms are active."
                    WeatherType.Snow         -> "It's snowing heavily."
                    WeatherType.Clouds       -> "It's overcast and cloudy."
                }
                val feelScenario = when {
                    data.feelsLike == null -> "fetching data"
                    data.feelsLike < 10   -> "very cold, definitely bundle up"
                    data.feelsLike < 20   -> "a bit chilly, bring a light jacket"
                    data.feelsLike < 28   -> "nice and pleasant outside"
                    else                  -> "quite hot, stay hydrated"
                }
                "$mainEvent It's ${data.description} right now in ${data.location}, visibility is ${data.visibility}. The temperature feels like ${data.feelsLike ?: "__"}°C ($feelScenario). Today's rain probability is ${data.rainProb}."
            }

            Column(modifier = Modifier.fillMaxWidth().offset(y = contentBlockY).padding(horizontal = 32.dp).alpha(contentBlockAlpha).blur(if (settings.blur) contentBlockBlur else 0.dp)) {
                Text(text = dynamicQuote, color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val infiniteTransition = rememberInfiniteTransition(label = "")
                    val windRotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(animation = tween(2000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "")
                    val metrics = listOf(Pair(Icons.Default.FilterDrama, data.aqi), Pair(Icons.Default.NorthEast, data.wind), Pair(Icons.Default.Visibility, data.visibility), Pair(Icons.Default.WaterDrop, data.humidity))
                    metrics.forEach { (icon, label) ->
                        Row(modifier = Modifier.clip(RoundedCornerShape(percent = 50)).background(Color.White.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp).graphicsLayer { if (settings.debugRotateWindSpeed && icon == Icons.Default.NorthEast) rotationZ = windRotation })
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Updated-at row (center → left on scroll, pinned once docked)
            val excessDp = with(density) { excessScroll.toDp() }
            val updateY = (600f - 510f * smoothProgress).dp
            val providerName = settings.provider.lowercase()
            val cityInfoStr = if (settings.headerType == HeaderType.Standard) "" else "${data.location.lowercase()} - "
            val updateText = "${cityInfoStr}Updated at ${data.lastUpdated} from $providerName"

            // ── Widgets column ─────────────────────────────────────────────
            val widgetYBase = (650f - 500f * smoothProgress).dp
            val widgetY = widgetYBase - excessDp
            val widgetBg = remember { Color.White.copy(alpha = 0.15f) }

            val mapToIcon = remember { { t: WeatherType ->
                when (t) {
                    WeatherType.Clear                             -> Icons.Default.WbSunny
                    WeatherType.Clouds, WeatherType.Atmosphere   -> Icons.Default.FilterDrama
                    WeatherType.Rain, WeatherType.Drizzle         -> Icons.Default.WaterDrop
                    WeatherType.Thunderstorm, WeatherType.Snow    -> Icons.Default.Thunderstorm
                }
            } }

            Column(modifier = Modifier.fillMaxWidth().offset(y = widgetY).padding(horizontal = 24.dp)) {

                // ── Hourly forecast ────────────────────────────────────────
                FullWidgetBox(icon = Icons.Default.WatchLater, label = "Hourly forecast") {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        data.hourlyForecast.forEach { item ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(mapToIcon(item.type), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(item.temp, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(item.time, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 10-Day forecast ────────────────────────────────────────
                FullWidgetBox(icon = Icons.Default.DateRange, label = "10-Day forecast") {
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        data.dailyForecast.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(item.day, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.4f))
                                Icon(mapToIcon(item.type), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.weight(0.5f))
                                Text(item.tempMin, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.width(8.dp))
                                // temp bar
                                Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                                    val pop = item.pop / 100f
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pop.coerceIn(0.05f, 1f)).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.7f)))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(item.temp, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 2-column grid: Precipitation + Humidity ────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Precipitation
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(widgetBg).padding(16.dp)) {
                        Column {
                            WidgetLabel(Icons.Default.WaterDrop, "Precipitation")
                            Spacer(Modifier.height(8.dp))
                            val precipMm = data.rainfallNext24h ?: 0.0
                            Text("${String.format("%.1f", precipMm)} mm", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("expected today", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            // progress bar
                            val frac = (precipMm / 50.0).coerceIn(0.0, 1.0).toFloat()
                            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(frac.coerceAtLeast(0.03f)).clip(RoundedCornerShape(50)).background(Color(0xFF81D4FA)))
                            }
                        }
                    }

                    // Humidity
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(widgetBg).padding(16.dp)) {
                        Column {
                            WidgetLabel(Icons.Default.WaterDrop, "Humidity")
                            Spacer(Modifier.height(8.dp))
                            val hVal = data.humidityValue ?: 0
                            Text("${hVal}%", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(when {
                                hVal > 80 -> "Very humid"
                                hVal > 60 -> "Humid"
                                hVal > 40 -> "Comfortable"
                                else      -> "Dry"
                            }, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth((hVal / 100f).coerceAtLeast(0.03f)).clip(RoundedCornerShape(50)).background(Color(0xFF4FC3F7)))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 2-column grid: Feels Like + UV Index ───────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Feels Like
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(widgetBg).padding(16.dp)) {
                        Column {
                            WidgetLabel(Icons.Default.DeviceThermostat, "Feels like")
                            Spacer(Modifier.height(8.dp))
                            Text("${data.feelsLike ?: "__"}°", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text(data.feelsLikeDesc, color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                        }
                    }

                    // UV Index
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(widgetBg).padding(16.dp)) {
                        Column {
                            WidgetLabel(Icons.Default.WbSunny, "UV Index")
                            Spacer(Modifier.height(8.dp))
                            val uv = data.uvIndex
                            Text(if (uv != null) String.format("%.1f", uv) else "__", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                            Text(when {
                                uv == null  -> "--"
                                uv < 3      -> "Low"
                                uv < 6      -> "Moderate"
                                uv < 8      -> "High"
                                uv < 11     -> "Very High"
                                else        -> "Extreme"
                            }, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            if (uv != null) {
                                val frac = (uv / 12.0).coerceIn(0.0, 1.0).toFloat()
                                val uvColor = when {
                                    uv < 3  -> Color(0xFF66BB6A)
                                    uv < 6  -> Color(0xFFFFEE58)
                                    uv < 8  -> Color(0xFFFFA726)
                                    uv < 11 -> Color(0xFFEF5350)
                                    else    -> Color(0xFFAB47BC)
                                }
                                Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))) {
                                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(frac.coerceAtLeast(0.03f)).clip(RoundedCornerShape(50)).background(uvColor))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Location Map ──────────────────────────────────────────
                if (data.lat != null && data.lon != null) {
                    val lat = data.lat
                    val lon = data.lon
                    val mapHtml = remember(lat, lon) { """
                        <!DOCTYPE html><html><head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                        <style>body{margin:0;padding:0}#map{width:100%;height:100vh;}</style>
                        </head><body>
                        <div id="map"></div>
                        <script>
                        var map = L.map('map', {zoomControl:false, attributionControl:false, dragging:false, scrollWheelZoom:false, touchZoom:false, doubleClickZoom:false}).setView([$lat,$lon], 10);
                        L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png').addTo(map);
                        L.circleMarker([$lat,$lon], {radius:6, color:'#fff', fillColor:'#4fc3f7', fillOpacity:1, weight:2}).addTo(map);
                        </script></body></html>
                    """.trimIndent() }

                    Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp))) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).also { wv ->
                                    wv.settings.javaScriptEnabled = true
                                    wv.settings.domStorageEnabled = true
                                    wv.webViewClient = WebViewClient()
                                    wv.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    wv.loadDataWithBaseURL("https://openstreetmap.org", mapHtml, "text/html", "utf-8", null)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Label overlay
                        Box(modifier = Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF81D4FA), modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${data.location.uppercase()} MAP", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 2-column grid: Wind + Air Quality ─────────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Wind compass
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(widgetBg).padding(16.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WidgetLabel(Icons.Default.NorthEast, "Wind")
                            Spacer(Modifier.height(6.dp))
                            val deg = data.windDeg ?: 0
                            Canvas(modifier = Modifier.size(70.dp)) {
                                val cx = size.width / 2
                                val cy = size.height / 2
                                val r = size.width / 2 - 4.dp.toPx()
                                // circle
                                drawCircle(color = Color.White.copy(alpha = 0.15f), radius = r)
                                // cardinal points dots
                                for (d in 0..315 step 45) {
                                    val rd = Math.toRadians(d.toDouble())
                                    val px = cx + (r - 6.dp.toPx()) * sin(rd).toFloat()
                                    val py = cy - (r - 6.dp.toPx()) * cos(rd).toFloat()
                                    drawCircle(color = Color.White.copy(alpha = 0.4f), radius = 2.dp.toPx(), center = Offset(px, py))
                                }
                                // arrow
                                val rad = Math.toRadians(deg.toDouble())
                                val tip = Offset(cx + (r - 8.dp.toPx()) * sin(rad).toFloat(), cy - (r - 8.dp.toPx()) * cos(rad).toFloat())
                                val tail = Offset(cx - 20.dp.toPx() * sin(rad).toFloat(), cy + 20.dp.toPx() * cos(rad).toFloat())
                                drawLine(Color.White, tail, tip, strokeWidth = 2.5f, cap = StrokeCap.Round)
                                drawCircle(Color(0xFF4FC3F7), radius = 4.dp.toPx(), center = tip)
                            }
                            Text(data.wind, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (data.windGust != "--") Text("Gust: ${data.windGust}", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        }
                    }

                    // Air Quality
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(widgetBg).padding(16.dp)) {
                        Column {
                            WidgetLabel(Icons.Default.FilterDrama, "Air Quality")
                            Spacer(Modifier.height(8.dp))
                            val aqi = data.aqiValue ?: 0
                            val aqiLabel = when (aqi) { 1 -> "Good"; 2 -> "Fair"; 3 -> "Moderate"; 4 -> "Poor"; 5 -> "Very Poor"; else -> "--" }
                            val aqiColor = when (aqi) { 1 -> Color(0xFF66BB6A); 2 -> Color(0xFFFFEE58); 3 -> Color(0xFFFFA726); 4 -> Color(0xFFEF5350); else -> Color(0xFFAB47BC) }
                            Text(aqiLabel, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("AQI index $aqi/5", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            Spacer(Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(Color(0xFF66BB6A), Color(0xFFFFEE58), Color(0xFFFFA726), Color(0xFFEF5350), Color(0xFFAB47BC)).forEachIndexed { i, c ->
                                    val isFilled = i < aqi
                                    Box(modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(50)).background(if (isFilled) c else Color.White.copy(alpha = 0.2f)))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 2-column grid: Visibility + Pressure ──────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                    // Visibility
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(widgetBg).padding(16.dp)) {
                        Column {
                            WidgetLabel(Icons.Default.Visibility, "Visibility")
                            Spacer(Modifier.height(8.dp))
                            Text(data.visibility, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            val visKm = data.visibilityM?.div(1000) ?: 0
                            Text(when {
                                visKm >= 10 -> "Clear view"
                                visKm >= 5  -> "Mostly clear"
                                visKm >= 2  -> "Light haze"
                                else        -> "Dense fog"
                            }, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }

                    // Pressure dial
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(widgetBg).padding(16.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            WidgetLabel(Icons.Default.Compress, "Pressure")
                            Spacer(Modifier.height(6.dp))
                            val pHpa = data.pressure ?: 1013
                            // dial
                            Canvas(modifier = Modifier.size(70.dp)) {
                                val cx = size.width / 2; val cy = size.height / 2
                                val r = size.width / 2 - 4.dp.toPx()
                                // tick marks
                                for (t in 0..120 step 10) {
                                    val ang = Math.toRadians(180.0 + t * 1.5)
                                    val inner = r - 8.dp.toPx()
                                    val px1 = cx + inner * cos(ang).toFloat(); val py1 = cy + inner * sin(ang).toFloat()
                                    val px2 = cx + r * cos(ang).toFloat(); val py2 = cy + r * sin(ang).toFloat()
                                    drawLine(Color.White.copy(alpha = 0.3f), Offset(px1, py1), Offset(px2, py2), strokeWidth = 1.5f)
                                }
                                // needle (950..1050 -> 0..180 deg sweep)
                                val norm = ((pHpa - 950) / 100f).coerceIn(0f, 1f)
                                val needleAng = Math.toRadians(180.0 + norm * 180.0)
                                val nx = cx + (r - 10.dp.toPx()) * cos(needleAng).toFloat()
                                val ny = cy + (r - 10.dp.toPx()) * sin(needleAng).toFloat()
                                drawLine(Color.White, Offset(cx, cy), Offset(nx, ny), strokeWidth = 2.5f, cap = StrokeCap.Round)
                                drawCircle(Color(0xFF4FC3F7), radius = 4.dp.toPx(), center = Offset(cx, cy))
                            }
                            Text("$pHpa hPa", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Low", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                                Text("High", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Sunrise / Sunset widget (full width) ───────────────────
                FullWidgetBox(icon = Icons.Default.WbSunny, label = "Sunrise & Sunset") {
                    val sunriseStr = if (data.sunriseEpoch != null) epochToTimeStr(data.sunriseEpoch) else "--"
                    val sunsetStr  = if (data.sunsetEpoch  != null) epochToTimeStr(data.sunsetEpoch)  else "--"

                    // Calculate sun arc progress
                    val nowMs = System.currentTimeMillis() / 1000L
                    val sunProgress = if (data.sunriseEpoch != null && data.sunsetEpoch != null && nowMs in data.sunriseEpoch..data.sunsetEpoch) {
                        ((nowMs - data.sunriseEpoch).toFloat() / (data.sunsetEpoch - data.sunriseEpoch).toFloat()).coerceIn(0f, 1f)
                    } else if (data.sunriseEpoch != null && nowMs < data.sunriseEpoch) 0f else 1f

                    Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                        val w = size.width; val h = size.height
                        val arcLeft = 16.dp.toPx(); val arcRight = w - 16.dp.toPx()
                        val arcTop = 8.dp.toPx(); val arcBottom = h + 20.dp.toPx()

                        // dashed horizon line
                        val dashW = 6.dp.toPx(); val gapW = 4.dp.toPx()
                        var x = arcLeft
                        while (x < arcRight) {
                            drawLine(Color.White.copy(alpha = 0.25f), Offset(x, h - 20.dp.toPx()), Offset(x + dashW, h - 20.dp.toPx()), strokeWidth = 1.5f)
                            x += dashW + gapW
                        }

                        // arc path
                        val path = Path().apply {
                            moveTo(arcLeft, h - 20.dp.toPx())
                            cubicTo(arcLeft, arcTop, arcRight, arcTop, arcRight, h - 20.dp.toPx())
                        }
                        drawPath(path, color = Color.White.copy(alpha = 0.3f), style = Stroke(width = 2.dp.toPx()))

                        // sun dot on arc
                        val t = sunProgress
                        val sunX = arcLeft + (arcRight - arcLeft) * t
                        val sunY = h - 20.dp.toPx() -
                            (3 * (1 - t) * t * t * (h - 20.dp.toPx() - arcTop) + 3 * t * t * (1 - t) * (h - 20.dp.toPx() - arcTop)) // approx parabola
                        // Simplified: parabola
                        val sunYSimple = h - 20.dp.toPx() - (4 * t * (1 - t)) * (h - arcTop - 20.dp.toPx())
                        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(sunX, sunYSimple))
                        drawCircle(Color(0xFFFFECB3), radius = 5.dp.toPx(), center = Offset(sunX, sunYSimple))
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("SUNRISE", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                            Text(sunriseStr, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("SUNSET", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
                            Text(sunsetStr, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Rainfall widget (full width) ───────────────────────────
                FullWidgetBox(icon = Icons.Default.WaterDrop, label = "Rainfall") {
                    val last24 = data.rainfallLast24h ?: 0.0
                    val next24 = data.rainfallNext24h ?: 0.0
                    Text("${String.format("%.0f", last24)} mm", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                    Text("in last 24h", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "${String.format("%.0f", next24)} mm expected in next 24h.",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                // ── Moon Phase widget (full width) ─────────────────────────
                FullWidgetBox(icon = Icons.Default.NightsStay, label = "Moon Phase") {
                    val phase = data.moonPhase ?: 0.0
                    val phaseName = when {
                        phase < 0.03 || phase > 0.97 -> "New Moon"
                        phase < 0.22 -> "Waxing Crescent"
                        phase < 0.28 -> "First Quarter"
                        phase < 0.47 -> "Waxing Gibbous"
                        phase < 0.53 -> "Full Moon"
                        phase < 0.72 -> "Waning Gibbous"
                        phase < 0.78 -> "Last Quarter"
                        else         -> "Waning Crescent"
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        // Moon disk drawn with Canvas
                        Canvas(modifier = Modifier.size(72.dp)) {
                            val cx = size.width / 2; val cy = size.height / 2; val r = size.width / 2 - 2.dp.toPx()
                            // dark background circle
                            drawCircle(Color.White.copy(alpha = 0.1f), radius = r)
                            // illuminated portion (simplified as fraction fill)
                            val illumination = when {
                                phase <= 0.5 -> phase * 2  // waxing: 0→1
                                else -> (1.0 - phase) * 2  // waning: 1→0
                            }
                            val litColor = Color(0xFFFFECB3)
                            val shadowColor = Color(0xFF1A1A2E)
                            // Draw full moon circle first
                            drawCircle(litColor.copy(alpha = 0.9f), radius = r)
                            // Overlay shadow to simulate phase
                            if (phase < 0.5) {
                                // Waxing: shadow on left side
                                val shadowFrac = (1.0 - illumination).toFloat()
                                val shadowXOffset = (shadowFrac * 2 - 1) * r
                                drawCircle(shadowColor, radius = r, center = Offset(cx + shadowXOffset, cy))
                            } else {
                                // Waning: shadow on right side
                                val shadowFrac = (1.0 - illumination).toFloat()
                                val shadowXOffset = (1 - shadowFrac * 2) * r
                                drawCircle(shadowColor, radius = r, center = Offset(cx + shadowXOffset, cy))
                            }
                        }

                        Column {
                            Text(phaseName, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("${(phase * 100).toInt()}% through cycle", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(120.dp))
            }

            // ── Pinned header (on top of widgets) ──────────────────────────
            // Frosted gradient behind header (only visible when scrolled)
            val headerBlurAlpha = smoothProgress.coerceIn(0f, 1f)
            if (headerBlurAlpha > 0.01f) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp)
                    .alpha(headerBlurAlpha)
                    .background(
                        Brush.verticalGradient(
                            0f to skyTopColor.copy(alpha = 0.98f),
                            0.6f to skyTopColor.copy(alpha = 0.85f),
                            0.85f to skyTopColor.copy(alpha = 0.4f),
                            1f to Color.Transparent
                        )
                    )
                )
            }

            // Pinned title, dash, temp
            Text(text = headerText, color = Color.White.copy(alpha = 0.9f), fontSize = titleSize, fontWeight = titleWeight, modifier = Modifier.offset(x = titleX, y = titleY))
            if (settings.headerType != HeaderType.Disabled) {
                Text(text = "-", color = Color.White.copy(alpha = dashAlpha), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.offset(x = dashX, y = titleY))
            }
            Text(text = "${data.temp ?: "__"}°", color = Color.White.copy(alpha = tempAlpha), fontSize = tempSize, fontWeight = FontWeight.Bold, modifier = Modifier.offset(x = tempX, y = tempY))

            // Pinned "Updated at" row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.offset(y = updateY).layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val centerX = (constraints.maxWidth - placeable.width) / 2
                    val leftX = 32.dp.roundToPx()
                    val currentX = centerX + (leftX - centerX) * smoothProgress
                    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(currentX.toInt(), 0) }
                }
            ) {
                Icon(Icons.Default.Navigation, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(10.dp).graphicsLayer { rotationZ = 45f })
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = updateText, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (settings.enableClouds) {
            Box(modifier = Modifier.fillMaxSize().drawWithCache {
                cloudFrontShader.setFloatUniform("iResolutionX", size.width)
                cloudFrontShader.setFloatUniform("iResolutionY", size.height)
                cloudFrontShader.setFloatUniform("iTime", time)
                cloudFrontShader.setFloatUniform("scrollOffset", scrollOffset / size.height)
                cloudFrontShader.setColorUniform("cloudColor", cloudColor.toArgb())
                cloudFrontShader.setFloatUniform("cloudDensityMult", cloudDensityMult)
                cloudFrontShader.setFloatUniform("windSpeedMult", windSpeedMult)
                cloudFrontShader.setFloatUniform("layerSeed", 12f)
                cloudFrontShader.setFloatUniform("layerAlphaMult", 0.45f)
                cloudFrontShader.setFloatUniform("isFog", isFogAnim)
                onDrawBehind { drawRect(brush = ShaderBrush(cloudFrontShader)) }
            })
        }
    }
}

private fun epochToTimeStr(epoch: Long): String {
    val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epoch * 1000))
}