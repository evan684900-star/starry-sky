#version 150

// Starry Sky - shooting star fragment shader.
// Soft trail: bright along its centre line, fading towards the edges and towards the tail,
// with a hotter, whiter head. Added to the sky (blending ONE, ONE), like the stars.

uniform vec4 ColorModulator; // global brightness (night visibility * rain * "brightness" option)

in vec2 trailCoord;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // Across the trail: 1 on the centre line, 0 on the edges, with a soft profile.
    float across = 1.0 - trailCoord.x * trailCoord.x;
    across *= across;

    // Along the trail: the light builds up towards the head.
    float along = clamp(trailCoord.y, 0.0, 1.0);
    float tail = along * along;
    float head = smoothstep(0.80, 1.0, along);

    vec3 color = mix(vertexColor.rgb, vec3(1.0), head * 0.7);
    float light = across * (tail * 0.7 + head * 0.8);

    fragColor = vec4(color * (light * vertexColor.a) * ColorModulator.rgb, 0.0);
}
