#version 150

// Starry Sky - shooting star vertex shader (DefaultVertexFormat.POSITION_TEX_COLOR).
// (Comments are kept ASCII-only: some GPU drivers reject non-ASCII characters in GLSL sources.)
//
//   UV0.x : position across the trail (-1 = left edge, 1 = right edge)
//   UV0.y : position along the trail (0 = end of the tail, 1 = head)
//   Color : RGB = tint ; A = opacity (fade in / fade out of the whole shooting star)

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 trailCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    trailCoord = UV0;
    vertexColor = Color;
}
