#version 150

// Starry Sky - Milky Way glow vertex shader (DefaultVertexFormat.POSITION_COLOR).
// (Comments are kept ASCII-only: some GPU drivers reject non-ASCII characters in GLSL sources.)
//
// The glow is a static mesh wrapped around the galactic band. Its colours are computed once on the
// CPU and stored per vertex (RGB = tint, A = density), so drawing it costs almost nothing:
// all the work is done here, per vertex, and the fragment shader has next to nothing left to do.

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;   // camera * sky rotation
uniform mat4 ProjMat;
uniform mat4 SkyMat;         // sky rotation only: gives the real altitude above the horizon
uniform vec4 ColorModulator; // glow strength (night visibility * rain * "brightness" option)

out vec3 glowColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // A faint glow is the first thing to drown in the haze near the horizon.
    float altitude = normalize((SkyMat * vec4(Position, 0.0)).xyz).y;
    float extinction = smoothstep(0.0, 0.45, altitude);

    glowColor = Color.rgb * ColorModulator.rgb * (Color.a * extinction);
}
