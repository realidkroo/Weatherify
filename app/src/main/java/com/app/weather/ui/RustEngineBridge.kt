package com.app.weather.ui

import android.view.Surface
import android.util.Log

object RustEngineBridge {
    var isLoaded = false

    init {
        try {
            System.loadLibrary("rust_engine")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("RustEngineBridge", "Failed to load Rust engine library: ${e.message}")
        }
    }

    fun safeInitEngine(surface: Surface) {
        if (isLoaded) {
            initEngine(surface)
        }
    }

    fun safeSetScrollOffset(offset: Float) {
        if (isLoaded) {
            setScrollOffset(offset)
        }
    }

    @JvmStatic
    private external fun initEngine(surface: Surface)

    @JvmStatic
    private external fun setScrollOffset(offset: Float)
}
