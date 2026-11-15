#version 150

// Starry Sky - aurora fragment shader.
// A curtain of light: bright lower edge fading towards the top, fine vertical rays drifting along it,
// large slow brightness waves, green at the bottom turning teal then violet at the top.
// Added to the sky (blending ONE, ONE). A handful of sin() per pixel: cheap even on integrated graphics.

uniform vec4 ColorModulator; // overall strength (cold biome fade * night visibility)
uniform float AuroraTime;
uniform float AuroraStorm;   // 0 = normal night, 1 = geomagnetic storm (red and pink upper curtains)

in vec2 curtain;
in float seed;
in float strength;

out vec4 fragColor;

void main() {
    float u = curtain.x;
    float v = curtain.y;

    // Vertical profile: soft but defined lower edge, long fade to the top.
    float profile = smoothstep(0.0, 0.22, v) * pow(1.0 - v, 1.3);
    // The lower edge is not straight: it ripples with the rays.
    profile *= 0.75 + 0.25 * sin(u * 61.0 + AuroraTime * 0.5 + seed * 3.0);
    // Both ends of the curtain fade out.
    float ends = smoothstep(0.0, 0.12, u) * (1.0 - smoothstep(0.88, 1.0, u));

    // Fine vertical rays drifting along the curtain.
    float rays = 0.55 + 0.45 * sin(u * 230.0 + AuroraTime * 0.9 + sin(u * 31.0 + AuroraTime * 0.4 + seed * 10.0) * 2.5);
    rays *= 0.7 + 0.3 * sin(u * 97.0 - AuroraTime * 0.6 + seed * 4.0);
    // Large, slow brightness waves running along the curtain.
    float waves = 0.35 + 0.65 * pow(0.5 + 0.5 * sin(u * 8.0 - AuroraTime * 0.18 + seed * 6.28), 1.5);

    float light = profile * ends * mix(0.45, 1.0, rays) * waves * strength;

    vec3 green = vec3(0.20, 1.00, 0.55);
    vec3 teal = vec3(0.15, 0.75, 0.85);
    vec3 violet = vec3(0.65, 0.30, 0.95);
    vec3 color = mix(green, teal, smoothstep(0.30, 0.55, v));
    color = mix(color, violet, smoothstep(0.45, 0.85, v));
    // Faint pink fringe along the very bottom edge.
    color = mix(color, vec3(1.0, 0.35, 0.6), (1.0 - smoothstep(0.0, 0.10, v)) * 0.45);
    // Storm nights: the upper part turns crimson and pink, like rare strong auroras.
    vec3 stormTop = mix(vec3(1.0, 0.30, 0.45), vec3(0.95, 0.15, 0.25), smoothstep(0.6, 1.0, v));
    color = mix(color, stormTop, AuroraStorm * smoothstep(0.35, 0.75, v));
    // The violet top is dimmer in absolute terms: lift it a little so it stays visible.
    light *= 1.0 + 0.8 * smoothstep(0.45, 0.85, v);

    fragColor = vec4(color * light * ColorModulator.rgb, 0.0);
}
