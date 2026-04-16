package com.app.weather.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    options: List<String>,
    selectedIndex: Int,
    onIndexSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 44.dp
    val visibleItems = 7
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - visibleItems / 2).coerceAtLeast(0)
    )
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    val layoutInfo = listState.layoutInfo
                    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                    val closest = layoutInfo.visibleItemsInfo.minByOrNull { item ->
                        val itemCenter = item.offset + item.size / 2f
                        abs(itemCenter - viewportCenter)
                    }
                    closest?.let { item ->
                        if (item.index != selectedIndex && item.index in options.indices) {
                            onIndexSelected(item.index)
                        }
                    }
                }
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * visibleItems)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItems / 2)),
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()

                    // Top fade
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.White),
                            startY = 0f,
                            endY = itemHeightPx * 2f
                        ),
                        blendMode = BlendMode.DstIn
                    )

                    // Bottom fade
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White, Color.Transparent),
                            startY = size.height - itemHeightPx * 2f,
                            endY = size.height
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(options.size) { index ->
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth()
                        .graphicsLayer {
                            // Calculating offset inside the draw phase eliminates ALL "flying" lag
                            val layoutInfo = listState.layoutInfo
                            val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                            
                            val normalizedOffset = if (itemInfo != null) {
                                val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                (itemCenter - viewportCenter) / itemHeightPx
                            } else {
                                if (index < listState.firstVisibleItemIndex) -4f else 4f
                            }

                            // True cylinder math
                            val anglePerItemDeg = 28f
                            val rotationDeg = (normalizedOffset * anglePerItemDeg).coerceIn(-90f, 90f)
                            val angleRad = Math.toRadians(rotationDeg.toDouble()).toFloat()

                            val radiusPx = itemHeightPx / sin(Math.toRadians(anglePerItemDeg.toDouble())).toFloat()
                            
                            val linearY = normalizedOffset * itemHeightPx
                            val projectedY = radiusPx * sin(angleRad)
                            val shiftY = projectedY - linearY 
                            
                            val scale = (cos(angleRad) * 0.9f + 0.1f).coerceIn(0.6f, 1f)
                            val itemAlpha = (cos(angleRad)).coerceIn(0f, 1f)

                            translationY = shiftY
                            rotationX = rotationDeg
                            alpha = itemAlpha
                            scaleX = scale
                            scaleY = scale
                            cameraDistance = 12f * density.density
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = options[index],
                        color = Color.White,
                        fontSize = 22.sp,
                        // Update bold state cleanly without triggering re-composition lag
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}