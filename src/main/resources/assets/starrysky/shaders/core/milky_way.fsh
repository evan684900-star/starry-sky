#version 150

// Starry Sky - Milky Way glow fragment shader.
// Adds the (very faint) interpolated glow to the sky. A tiny screen-space dither hides the colour
// banding that such a dark, smooth gradient would otherwise show on an 8-bit screen.
// The dither is "interleaved gradient noise": two fract() and one dot(), no sin(), so this
// full-screen pass stays cheap even on integrated graphics.

in vec3 glowColor;

out vec4 fragColor;

void main() {
    float noise = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    fragColor = vec4(max(glowColor + vec3((noise - 0.5) / 255.0), vec3(0.0)), 0.0);
}
