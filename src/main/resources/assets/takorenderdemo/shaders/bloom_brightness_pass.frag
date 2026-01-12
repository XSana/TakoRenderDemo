#version 330 core

in vec2 uv;

out vec4 fragColor;

uniform sampler2D texture0;
uniform vec2 resolution; // viewport resolution in pixels

const float brightPassThreshold = 1.0;
const vec3 luminanceVector = vec3(0.2125, 0.7154, 0.0721);

void main() {
  vec4 c = texture(texture0, uv);

  float luminance = dot(luminanceVector, c.xyz);
  luminance = max(0.0, luminance - brightPassThreshold);
  c.xyz *= sign(luminance);
  c.a = 1.0;

  fragColor = c;
}
