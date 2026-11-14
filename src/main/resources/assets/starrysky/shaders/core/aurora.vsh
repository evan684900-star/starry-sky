#version 150

// Starry Sky - aurora vertex shader (DefaultVertexFormat.POSITION_TEX_COLOR).
// (Comments are kept ASCII-only: some GPU drivers reject non-ASCII characters in GLSL sources.)
//
// The aurora is a static mesh of a few curtains hanging high in the northern sky, in world space
// (it does not turn with the stars). Only the slow sway is animated here, per vertex.
//   UV0.x : position along the curtain (0..1)
//   UV0.y : height inside the curtain (0 = lower edge, 1 = top)
//   Color : R = per-curtain random value ; A = curtain strength

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;   // camera rotation only
uniform mat4 ProjMat;
uniform float AuroraTime;    // seconds

out vec2 curtain;
out float seed;
out float strength;

void main() {
    vec3 pos = Position;
    seed = Color.r;

    // Slow sideways sway, stronger at the top of the curtain.
    vec3 side = normalize(vec3(-pos.z, 0.0, pos.x));
    float sway = sin(AuroraTime * 0.21 + UV0.x * 7.0 + seed * 20.0) * 1.8
               + sin(AuroraTime * 0.13 - UV0.x * 13.0 + seed * 9.0) * 1.1;
    pos += side * sway * (0.4 + 0.6 * UV0.y);

    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    curtain = UV0;
    strength = Color.a;
}
