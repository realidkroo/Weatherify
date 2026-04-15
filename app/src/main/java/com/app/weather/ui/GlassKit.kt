package com.app.weather.ui

import android.graphics.RenderNode
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip

@Stable
class GlassState {
    var renderNode by mutableStateOf<RenderNode?>(null)
    var rootPosition by mutableStateOf(Offset.Zero)
}

fun Modifier.glassRoot(state: GlassState): Modifier = this
    .onGloballyPositioned { state.rootPosition = it.positionInWindow() }
    .drawWithCache {
        val node = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RenderNode("GlassRoot").apply {
                setPosition(0, 0, size.width.toInt(), size.height.toInt())
            }
        } else null
        
        // Safe to assign the reference here as it doesn't change per-frame
        state.renderNode = node

        onDrawWithContent {
            val drawContextCanvas = drawContext.canvas
            if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val nativeCanvas = node.beginRecording()
                val composeCanvas = Canvas(nativeCanvas)

                drawContext.canvas = composeCanvas
                drawContent()
                
                drawContext.canvas = drawContextCanvas
                node.endRecording()
                
                drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
            } else {
                drawContent()
            }
        }
    }

@Composable
fun GlassPillBackground(
    state: GlassState,
    blurRadius: Float = 24f,
    tint: Color = Color.Transparent,
    shape: Shape? = null,
    modifier: Modifier = Modifier
) {
    var position by remember { mutableStateOf(Offset.Zero) }

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .onGloballyPositioned { position = it.positionInWindow() }
            .then(if (shape != null) Modifier.clip(shape) else Modifier)
            .graphicsLayer {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0f) {
                    renderEffect = android.graphics.RenderEffect
                        .createBlurEffect(blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
                clip = true
            }
    ) {
        val node = state.renderNode
        if (node != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val relX = position.x - state.rootPosition.x
            val relY = position.y - state.rootPosition.y
            translate(left = -relX, top = -relY) {
                drawIntoCanvas { it.nativeCanvas.drawRenderNode(node) }
            }
        }
        if (tint != Color.Transparent) {
            drawRect(color = tint)
        }
    }
}
