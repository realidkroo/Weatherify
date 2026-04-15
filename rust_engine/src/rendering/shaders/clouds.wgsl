// WGSL shader for rendering volumetric clouds
// This is a minimal raymarching loop for atmospheric effects.

struct VertexOutput {
    @builtin(position) clip_position: vec4<f32>,
    @location(0) uv: vec2<f32>,
};

@vertex
fn vs_main(
    @builtin(vertex_index) in_vertex_index: u32,
) -> VertexOutput {
    var out: VertexOutput;
    
    // Generate fullscreen quad
    let x = f32(1 - i32(in_vertex_index)) * 5.0;
    let y = f32(i32(in_vertex_index & 1u) * 2 - 1) * 5.0;
    
    out.clip_position = vec4<f32>(x, y, 0.0, 1.0);
    out.uv = vec2<f32>(x * 0.5 + 0.5, y * 0.5 + 0.5);
    return out;
}

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    // Basic stylized sky gradient mixed with basic noise stub for clouds
    let sky_color_top = vec3<f32>(0.1, 0.3, 0.6);
    let sky_color_bottom = vec3<f32>(0.8, 0.9, 1.0);
    let mix_factor = in.uv.y;
    
    let base_color = mix(sky_color_top, sky_color_bottom, mix_factor);
    
    // We would implement true raymarching here...
    return vec4<f32>(base_color, 1.0);
}
