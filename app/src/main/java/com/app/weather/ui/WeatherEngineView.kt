package com.app.weather.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WeatherEngineView(modifier: Modifier = Modifier, scrollOffset: Float = 0f) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            EngineSurfaceView(context)
        },
        update = { view ->
            // Pass the scroll offset to the Rust rendering engine via JNI to adjust cloud/text positioning
            view.updateScroll(scrollOffset)
        }
    )
}

class EngineSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    init {
        holder.addCallback(this)
        // Make surface background transparent to see through if needed
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(false)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // Native call to initialize Vulkan/WGPU with this Surface
        RustEngineBridge.safeInitEngine(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Native call to resize swapchain
        // RustEngineBridge.resize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // Native call to tear down engine resources
        // RustEngineBridge.destroyEngine()
    }

    fun updateScroll(offset: Float) {
        RustEngineBridge.safeSetScrollOffset(offset)
    }
}
