#version 150

// Starry Sky - star vertex shader.
// (Comments are kept ASCII-only: some GPU drivers reject non-ASCII characters in GLSL sources.)
//
// Each star is a sprite expanded here, on the GPU, with a size expressed in screen pixels:
//   Position : centre of the star on the sky sphere (identical for the 4 corners)
//   UV0.x    : corner sign * base half-size (world units at distance 100)
//   UV0.y    : corner sign * (1 + glint strength)
//   Color    : RGB = tint * intensity ; A = per-star random value (twinkle phase/frequency)
//
// Working in pixels gives two things for free:
//   - a star never gets smaller than a couple of pixels (it gets dimmer instead, keeping the same
//     total light), so tiny stars do not flicker when the sky rotates;
//   - stars stay perfectly round everywhere on screen.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;   // camera * sky rotation
uniform mat4 ProjMat;
uniform mat4 SkyMat;         // sky rotation only: gives the real altitude above the horizon
uniform vec2 ScreenSize;     // framebuffer size in pixels
uniform float StarTime;      // twinkle clock, in seconds, in [0, PERIOD)
uniform float TwinkleAmount; // 0 = no twinkle
uniform float StarSize;      // "starSize" option

out vec2 quadCoord;          // -1..1 across the sprite
out vec3 starColor;
out vec2 shape;              // x = sprite size relative to the star core, y = glint strength

const float TAU = 6.28318530718;
// Must match StarField.TWINKLE_PERIOD on the Java side.
const float PERIOD = 1000.0;
// Must match StarField.RADIUS on the Java side.
const float RADIUS = 100.0;
// Smallest half-size of a star, in pixels.
const float MIN_HALF_PX = 2.0;
// Stars with glints get a larger sprite to make room for the spikes.
const float GLINT_SCALE = 3.0;
// ProjMat[1][1] for a 70 degree vertical field of view: 1 / tan(35 deg).
const float REFERENCE_P11 = 1.42815;

void main() {
    vec2 corner = sign(UV0);
    float baseSize = abs(UV0.x);
    float glint = abs(UV0.y) - 1.0;

    // Altitude of the star above the horizon (-1..1).
    float altitude = normalize((SkyMat * vec4(Position, 0.0)).xyz).y;
    // Atmospheric extinction: stars fade near the horizon and vanish below it.
    float extinction = smoothstep(-0.03, 0.22, altitude);

    if (extinction <= 0.0) {
        // Below the horizon: collapse the sprite outside the screen, nothing is rasterized.
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        quadCoord = vec2(0.0);
        starColor = vec3(0.0);
        shape = vec2(1.0, 0.0);
        return;
    }

    // Half-size in pixels. Below MIN_HALF_PX the sprite stops shrinking and dims instead.
    // Size is computed for a reference field of view of 70 degrees. When zooming in (spyglass), stars only
    // grow a little (zoom^0.4) instead of turning into huge discs, and they get brighter, like in a real telescope.
    float zoom = max(abs(ProjMat[1][1]) / REFERENCE_P11, 1.0);
    float pxPerUnit = REFERENCE_P11 * ScreenSize.y * 0.5 / RADIUS;
    float naturalPx = baseSize * StarSize * pxPerUnit * pow(zoom, 0.4);
    float telescopeGain = pow(zoom, 0.25);
    float halfPx = max(naturalPx, MIN_HALF_PX);
    float energy = (naturalPx * naturalPx) / (halfPx * halfPx);

    float quadScale = glint > 0.001 ? GLINT_SCALE : 1.0;

    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    clip.xy += corner * (halfPx * quadScale) * 2.0 / ScreenSize * clip.w;
    gl_Position = clip;

    quadCoord = corner;
    shape = vec2(quadScale, glint);

    // Twinkle: two sine waves with per-star frequencies.
    // Frequencies are whole numbers of cycles per PERIOD, so the animation stays
    // continuous when the clock wraps back to 0.
    float seed = Color.a;
    float phase = seed * TAU;
    float n1 = floor(120.0 + fract(seed * 97.531) * 360.0);
    float n2 = floor(260.0 + fract(seed * 41.173) * 520.0);
    float a1 = TAU * fract(n1 * StarTime / PERIOD) + phase;
    float a2 = TAU * fract(n2 * StarTime / PERIOD) + phase * 3.1;
    float wave = 0.6 * sin(a1) + 0.4 * sin(a2);
    // More air to go through near the horizon: stronger twinkle.
    float amount = TwinkleAmount * mix(1.4, 0.8, clamp(altitude, 0.0, 1.0));
    float twinkle = 1.0 - clamp(amount * (0.5 + 0.5 * wave), 0.0, 0.95);
    // Slight colour shimmer while twinkling, like real stars low in the sky.
    vec3 shimmer = vec3(1.0) + amount * 0.18 * vec3(sin(a2), sin(a2 + 2.094), sin(a2 + 4.189));

    starColor = Color.rgb * shimmer * (twinkle * extinction * energy * telescopeGain);
}
