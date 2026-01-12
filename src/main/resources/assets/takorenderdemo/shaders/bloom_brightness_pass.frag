#version 330 core

uniform sampler2D texture0;
uniform vec2 resolution; // viewport resolution in pixels

const float brightPassThreshold = 0.8; // 降低阈值让更多区域产生泛光
const vec3 luminanceVector = vec3(0.2125, 0.7154, 0.0721);

out vec4 fragColor;

void main() {
  vec2 texCoord = gl_FragCoord.xy / resolution.xy;

  vec4 c = texture(texture0, texCoord);

  // 只对有内容的像素进行bloom（alpha > 0）
  if (c.a < 0.01) {
    fragColor = vec4(0.0);
    return;
  }

  float luminance = dot(luminanceVector, c.rgb);

  // 只提取超过阈值的亮度部分，而不是整个颜色
  float bloomFactor = max(0.0, luminance - brightPassThreshold) / max(luminance, 0.001);

  // 限制bloom强度
  bloomFactor = min(bloomFactor, 1.0);

  fragColor = vec4(c.rgb * bloomFactor, c.a);
}