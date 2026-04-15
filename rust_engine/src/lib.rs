use jni::objects::{JClass, JObject};
use jni::JNIEnv;
use jni::sys::jfloat;
use android_logger::Config;
use log::{info, LevelFilter};

#[no_mangle]
pub extern "system" fn Java_com_app_weather_ui_RustEngineBridge_initEngine(
    mut env: JNIEnv,
    _class: JClass,
    _surface: JObject,
) {
    android_logger::init_once(Config::default().with_max_level(LevelFilter::Info));
    info!("Rust Engine initializing on Android surface!");
    // Setup wgpu surface here based on JObject pointing to ANativeWindow
}

#[no_mangle]
pub extern "system" fn Java_com_app_weather_ui_RustEngineBridge_setScrollOffset(
    mut _env: JNIEnv,
    _class: JClass,
    offset: jfloat,
) {
    info!("UI scrolled to offset: {}", offset);
    // Update internal renderer state for cloud animation
}
