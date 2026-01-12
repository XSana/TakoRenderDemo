#version 330 core

in vec2 uv;

out vec4 fragColor;

uniform float tone = 1.0;
uniform float bloomStrength = 0.1;

uniform sampler2D texture0;
uniform sampler2D texture1;

uniform vec2 resolution; // viewport resolution in pixels

void main() {
  vec4 original = texture(texture0, uv);
  vec4 bloom = texture(texture1, uv);

  // 只在有内容的区域添加bloom，保持黑洞中心的黑色
  vec3 color = original.rgb * tone + bloom.rgb * bloomStrength * original.a;

  // 保持原始alpha
  fragColor = vec4(color, original.a);
}