#version 150

// Starry Sky - star fragment shader.
// Draws a round, soft point of light (bright core + faint halo) instead of a square,
// plus thin cross-shaped glints on the few brightest stars.
// The result is added to the sky (blending ONE, ONE): alpha is not used.

uniform vec4 ColorModulator; // global brightness (night visibility * rain * "brightness" option)

in vec2 quadCoord;
in vec3 starColor;
in vec2 shape;

out vec4 fragColor;

void main() {
    // Coordinates relative to the star core (1 = edge of the core disc).
    vec2 p = quadCoord * shape.x;
    float d2 = dot(p, p);

    float core = exp(-d2 * 6.0);
    float halo = exp(-d2 * 2.5) * 0.18;
    float light = (core + halo) * (1.0 - smoothstep(0.6, 1.0, d2));

    if (shape.y > 0.0) {
        // Glints: two thin crossed spikes fading towards the edge of the sprite.
        vec2 a = abs(p);
        float spikes = exp(-a.y * 7.0 - a.x * 1.3) + exp(-a.x * 7.0 - a.y * 1.3);
        float border = max(abs(quadCoord.x), abs(quadCoord.y));
        light += shape.y * 0.35 * spikes * (1.0 - smoothstep(0.7, 1.0, border));
    }

    fragColor = vec4(starColor * light * ColorModulator.rgb, 0.0);
}
