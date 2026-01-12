#version 330 core

const float PI = 3.14159265359;
const float EPSILON = 0.0001;
const float INFINITY = 1000000.0;

out vec4 fragColor;

uniform vec2 resolution; // viewport resolution in pixels
uniform float mouseX;
uniform float mouseY;

uniform float time; // time elapsed in seconds
uniform samplerCube galaxy;
uniform sampler2D colorMap;
uniform sampler2D mcBackground; // MC场景背景纹理

uniform float frontView = 0.0;
uniform float topView = 0.0;
uniform float cameraRoll = 0.0;

uniform float gravatationalLensing = 1.0;
uniform float renderBlackHole = 1.0;
uniform float mouseControl = 0.0;
uniform float fovScale = 1.0;

uniform float adiskEnabled = 1.0;
uniform float adiskParticle = 1.0;
uniform float adiskHeight = 0.2;
uniform float adiskLit = 0.5;
uniform float adiskDensityV = 1.0;
uniform float adiskDensityH = 1.0;
uniform float adiskNoiseScale = 1.0;
uniform float adiskNoiseLOD = 5.0;
uniform float adiskSpeed = 0.5;

// MC相机参数
uniform int useExternalCamera = 0;
uniform vec3 mcCameraPos;
uniform vec3 mcCameraDir;
uniform vec3 mcCameraUp;
uniform float mcFov = 70.0;

// 是否使用透明背景
uniform int transparentBackground = 1;

struct Ring {
  vec3 center;
  vec3 normal;
  float innerRadius;
  float outerRadius;
  float rotateSpeed;
};

///----
/// Simplex 3D Noise
/// by Ian McEwan, Ashima Arts
vec4 permute(vec4 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

float snoise(vec3 v) {
  const vec2 C = vec2(1.0 / 6.0, 1.0 / 3.0);
  const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);

  // First corner
  vec3 i = floor(v + dot(v, C.yyy));
  vec3 x0 = v - i + dot(i, C.xxx);

  // Other corners
  vec3 g = step(x0.yzx, x0.xyz);
  vec3 l = 1.0 - g;
  vec3 i1 = min(g.xyz, l.zxy);
  vec3 i2 = max(g.xyz, l.zxy);

  //  x0 = x0 - 0. + 0.0 * C
  vec3 x1 = x0 - i1 + 1.0 * C.xxx;
  vec3 x2 = x0 - i2 + 2.0 * C.xxx;
  vec3 x3 = x0 - 1. + 3.0 * C.xxx;

  // Permutations
  i = mod(i, 289.0);
  vec4 p = permute(permute(permute(i.z + vec4(0.0, i1.z, i2.z, 1.0)) + i.y +
                           vec4(0.0, i1.y, i2.y, 1.0)) +
                   i.x + vec4(0.0, i1.x, i2.x, 1.0));

  // Gradients
  // ( N*N points uniformly over a square, mapped onto an octahedron.)
  float n_ = 1.0 / 7.0; // N=7
  vec3 ns = n_ * D.wyz - D.xzx;

  vec4 j = p - 49.0 * floor(p * ns.z * ns.z); //  mod(p,N*N)

  vec4 x_ = floor(j * ns.z);
  vec4 y_ = floor(j - 7.0 * x_); // mod(j,N)

  vec4 x = x_ * ns.x + ns.yyyy;
  vec4 y = y_ * ns.x + ns.yyyy;
  vec4 h = 1.0 - abs(x) - abs(y);

  vec4 b0 = vec4(x.xy, y.xy);
  vec4 b1 = vec4(x.zw, y.zw);

  vec4 s0 = floor(b0) * 2.0 + 1.0;
  vec4 s1 = floor(b1) * 2.0 + 1.0;
  vec4 sh = -step(h, vec4(0.0));

  vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
  vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

  vec3 p0 = vec3(a0.xy, h.x);
  vec3 p1 = vec3(a0.zw, h.y);
  vec3 p2 = vec3(a1.xy, h.z);
  vec3 p3 = vec3(a1.zw, h.w);

  // Normalise gradients
  vec4 norm =
      taylorInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
  p0 *= norm.x;
  p1 *= norm.y;
  p2 *= norm.z;
  p3 *= norm.w;

  // Mix final noise value
  vec4 m =
      max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
  m = m * m;
  return 42.0 *
         dot(m * m, vec4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
}
///----

float ringDistance(vec3 rayOrigin, vec3 rayDir, Ring ring) {
  float denominator = dot(rayDir, ring.normal);
  float constant = -dot(ring.center, ring.normal);
  if (abs(denominator) < EPSILON) {
    return -1.0;
  } else {
    float t = -(dot(rayOrigin, ring.normal) + constant) / denominator;
    if (t < 0.0) {
      return -1.0;
    }

    vec3 intersection = rayOrigin + t * rayDir;

    // Compute distance to ring center
    float d = length(intersection - ring.center);
    if (d >= ring.innerRadius && d <= ring.outerRadius) {
      return t;
    }
    return -1.0;
  }
}

vec3 panoramaColor(sampler2D tex, vec3 dir) {
  vec2 uv = vec2(0.5 - atan(dir.z, dir.x) / PI * 0.5, 0.5 - asin(dir.y) / PI);
  return texture2D(tex, uv).rgb;
}

vec3 accel(float h2, vec3 pos) {
  float r2 = dot(pos, pos);
  float r5 = pow(r2, 2.5);
  vec3 acc = -1.5 * h2 * pos / r5 * 1.0;
  return acc;
}

vec4 quadFromAxisAngle(vec3 axis, float angle) {
  vec4 qr;
  float half_angle = (angle * 0.5) * 3.14159 / 180.0;
  qr.x = axis.x * sin(half_angle);
  qr.y = axis.y * sin(half_angle);
  qr.z = axis.z * sin(half_angle);
  qr.w = cos(half_angle);
  return qr;
}

vec4 quadConj(vec4 q) { return vec4(-q.x, -q.y, -q.z, q.w); }

vec4 quat_mult(vec4 q1, vec4 q2) {
  vec4 qr;
  qr.x = (q1.w * q2.x) + (q1.x * q2.w) + (q1.y * q2.z) - (q1.z * q2.y);
  qr.y = (q1.w * q2.y) - (q1.x * q2.z) + (q1.y * q2.w) + (q1.z * q2.x);
  qr.z = (q1.w * q2.z) + (q1.x * q2.y) - (q1.y * q2.x) + (q1.z * q2.w);
  qr.w = (q1.w * q2.w) - (q1.x * q2.x) - (q1.y * q2.y) - (q1.z * q2.z);
  return qr;
}

vec3 rotateVector(vec3 position, vec3 axis, float angle) {
  vec4 qr = quadFromAxisAngle(axis, angle);
  vec4 qr_conj = quadConj(qr);
  vec4 q_pos = vec4(position.x, position.y, position.z, 0);

  vec4 q_tmp = quat_mult(qr, q_pos);
  qr = quat_mult(q_tmp, qr_conj);

  return vec3(qr.x, qr.y, qr.z);
}

#define IN_RANGE(x, a, b) (((x) > (a)) && ((x) < (b)))

void cartesianToSpherical(in vec3 xyz, out float rho, out float phi,
                          out float theta) {
  rho = sqrt((xyz.x * xyz.x) + (xyz.y * xyz.y) + (xyz.z * xyz.z));
  phi = asin(xyz.y / rho);
  theta = atan(xyz.z, xyz.x);
}

// Convert from Cartesian to spherical coord (rho, phi, theta)
// https://en.wikipedia.org/wiki/Spherical_coordinate_system
vec3 toSpherical(vec3 p) {
  float rho = sqrt((p.x * p.x) + (p.y * p.y) + (p.z * p.z));
  float theta = atan(p.z, p.x);
  float phi = asin(p.y / rho);
  return vec3(rho, theta, phi);
}

vec3 toSpherical2(vec3 pos) {
  vec3 radialCoords;
  radialCoords.x = length(pos) * 1.5 + 0.55;
  radialCoords.y = atan(-pos.x, -pos.z) * 1.5;
  radialCoords.z = abs(pos.y);
  return radialCoords;
}

void ringColor(vec3 rayOrigin, vec3 rayDir, Ring ring, inout float minDistance,
               inout vec3 color) {
  float distance = ringDistance(rayOrigin, normalize(rayDir), ring);
  if (distance >= EPSILON && distance < minDistance &&
      distance <= length(rayDir) + EPSILON) {
    minDistance = distance;

    vec3 intersection = rayOrigin + normalize(rayDir) * minDistance;
    vec3 ringColor;

    {
      float dist = length(intersection);

      float v = clamp((dist - ring.innerRadius) /
                          (ring.outerRadius - ring.innerRadius),
                      0.0, 1.0);

      vec3 base = cross(ring.normal, vec3(0.0, 0.0, 1.0));
      float angle = acos(dot(normalize(base), normalize(intersection)));
      if (dot(cross(base, intersection), ring.normal) < 0.0)
        angle = -angle;

      float u = 0.5 - 0.5 * angle / PI;
      // HACK
      u += time * ring.rotateSpeed;

      vec3 color = vec3(0.0, 0.5, 0.0);
      // HACK
      float alpha = 0.5;
      ringColor = vec3(color);
    }

    color += ringColor;
  }
}

mat3 lookAt(vec3 origin, vec3 target, float roll) {
  vec3 rr = vec3(sin(roll), cos(roll), 0.0);
  vec3 ww = normalize(target - origin);
  vec3 uu = normalize(cross(ww, rr));
  vec3 vv = normalize(cross(uu, ww));

  return mat3(uu, vv, ww);
}

float sqrLength(vec3 a) { return dot(a, a); }

void adiskColor(vec3 pos, inout vec3 color, inout float alpha, inout float accumulatedAlpha) {
  float innerRadius = 2.6;
  float outerRadius = 12.0;

  // Density linearly decreases as the distance to the blackhole center
  // increases.
  float density = max(
      0.0, 1.0 - length(pos.xyz / vec3(outerRadius, adiskHeight, outerRadius)));
  if (density < 0.001) {
    return;
  }

  density *= pow(1.0 - abs(pos.y) / adiskHeight, adiskDensityV);

  // Set particale density to 0 when radius is below the inner most stable
  // circular orbit.
  density *= smoothstep(innerRadius, innerRadius * 1.1, length(pos));

  // Avoid the shader computation when density is very small.
  if (density < 0.001) {
    return;
  }

  vec3 sphericalCoord = toSpherical(pos);

  // Scale the rho and phi so that the particales appear to be at the correct
  // scale visually.
  sphericalCoord.y *= 2.0;
  sphericalCoord.z *= 4.0;

  density *= 1.0 / pow(sphericalCoord.x, adiskDensityH);
  density *= 16000.0;

  if (adiskParticle < 0.5) {
    color += vec3(0.0, 1.0, 0.0) * density * 0.02;
    return;
  }

  float noise = 1.0;
  for (int i = 0; i < int(adiskNoiseLOD); i++) {
    noise *= 0.5 * snoise(sphericalCoord * pow(float(i + 1), 2.0) * adiskNoiseScale) + 0.5;
    if (i % 2 == 0) {
      sphericalCoord.y += time * adiskSpeed;
    } else {
      sphericalCoord.y -= time * adiskSpeed;
    }
  }

  vec3 dustColor =
      texture(colorMap, vec2(sphericalCoord.x / outerRadius, 0.5)).rgb;

  vec3 contribution = density * adiskLit * dustColor * alpha * abs(noise);
  color += contribution;

  // 累积不透明度 - 增大系数让吸积盘更不透明
  float contributionLuminance = dot(contribution, vec3(0.2126, 0.7152, 0.0722));
  accumulatedAlpha += contributionLuminance * 3.0;
}

// 将世界空间方向转换回屏幕UV坐标
vec2 worldDirToScreenUV(vec3 worldDir, mat3 viewMatrix, float fov) {
  // 将世界方向转换到相机空间
  mat3 invView = transpose(viewMatrix); // 正交矩阵的逆是转置
  vec3 camDir = invView * worldDir;

  // 如果方向指向相机后方，返回无效UV
  if (camDir.z <= 0.0) {
    return vec2(-1.0);
  }

  // 投影到屏幕空间
  float tanHalfFov = tan(radians(fov) * 0.5);
  vec2 screenPos = camDir.xy / (camDir.z * tanHalfFov);

  // 考虑宽高比
  screenPos.x /= resolution.x / resolution.y;

  // 转换到UV坐标 [0, 1]
  vec2 uv = screenPos + 0.5;

  return uv;
}

vec4 traceColor(vec3 pos, vec3 dir, mat3 viewMatrix, float fov) {
  vec3 color = vec3(0.0);
  float alpha = 1.0;
  float accumulatedAlpha = 0.0;

  float STEP_SIZE = 0.1;
  dir *= STEP_SIZE;

  // 保存初始方向用于后续计算
  vec3 originalDir = normalize(dir);

  // Initial values
  vec3 h = cross(pos, dir);
  float h2 = dot(h, h);

  bool hitEventHorizon = false;

  for (int i = 0; i < 300; i++) {
    if (renderBlackHole > 0.5) {
      // If gravatational lensing is applied
      if (gravatationalLensing > 0.5) {
        vec3 acc = accel(h2, pos);
        dir += acc;
      }

      // Reach event horizon - 事件视界内是纯黑的
      if (dot(pos, pos) < 1.0) {
        hitEventHorizon = true;
        break;
      }

      float minDistance = INFINITY;

      if (false) {
        Ring ring;
        ring.center = vec3(0.0, 0.05, 0.0);
        ring.normal = vec3(0.0, 1.0, 0.0);
        ring.innerRadius = 2.0;
        ring.outerRadius = 6.0;
        ring.rotateSpeed = 0.08;
        ringColor(pos, dir, ring, minDistance, color);
      } else {
        if (adiskEnabled > 0.5) {
          adiskColor(pos, color, alpha, accumulatedAlpha);
        }
        if (accumulatedAlpha > 0.98) {
            accumulatedAlpha = 1.0;
            break;
        }

        if (dot(pos, pos) > 400.0 && dot(pos, dir) > 0.0) {
            break;
        }
      }
    }

    pos += dir;
  }

  // 事件视界内是纯黑的
  if (hitEventHorizon) {
    return vec4(0.0, 0.0, 0.0, 1.0);
  }

  // 获取弯曲后的光线方向
  vec3 bentDir = normalize(dir);

  // 采样背景
  if (transparentBackground > 0) {
    // 将弯曲后的方向转换为屏幕UV
    vec2 bentUV = worldDirToScreenUV(bentDir, viewMatrix, fov);

    // 检查UV是否有效
    if (bentUV.x >= 0.0 && bentUV.x <= 1.0 && bentUV.y >= 0.0 && bentUV.y <= 1.0) {
      // 采样MC背景纹理（使用弯曲后的UV）
      vec3 bgColor = texture(mcBackground, bentUV).rgb;
      // 混合吸积盘颜色和背景
      float diskAlpha = clamp(accumulatedAlpha, 0.0, 1.0);
      color = color + bgColor * (1.0 - diskAlpha);
      return vec4(color, 1.0);
    } else {
      // UV超出范围，使用cubemap或返回吸积盘颜色
      vec3 bgColor = texture(galaxy, bentDir).rgb;
      float diskAlpha = clamp(accumulatedAlpha, 0.0, 1.0);
      color = color + bgColor * (1.0 - diskAlpha);
      return vec4(color, 1.0);
    }
  } else {
    // 原始模式：使用cubemap
    vec3 rotatedDir = rotateVector(bentDir, vec3(0.0, 1.0, 0.0), time);
    color += texture(galaxy, rotatedDir).rgb * alpha;
    return vec4(color, 1.0);
  }
}

void main() {
  mat3 view;
  vec3 cameraPos;

  if (useExternalCamera > 0) {
    // 使用MC相机参数
    cameraPos = mcCameraPos;
    vec3 forward = normalize(mcCameraDir);
    vec3 right = normalize(cross(forward, mcCameraUp));
    vec3 up = cross(right, forward);
    view = mat3(right, up, forward);
  } else if (mouseControl > 0.5) {
    vec2 mouse = clamp(vec2(mouseX, mouseY) / resolution.xy, 0.0, 1.0) - 0.5;
    cameraPos = vec3(-cos(mouse.x * 10.0) * 15.0, mouse.y * 30.0,
                     sin(mouse.x * 10.0) * 15.0);
    vec3 target = vec3(0.0, 0.0, 0.0);
    view = lookAt(cameraPos, target, radians(cameraRoll));
  } else if (frontView > 0.5) {
    cameraPos = vec3(10.0, 1.0, 10.0);
    vec3 target = vec3(0.0, 0.0, 0.0);
    view = lookAt(cameraPos, target, radians(cameraRoll));
  } else if (topView > 0.5) {
    cameraPos = vec3(15.0, 15.0, 0.0);
    vec3 target = vec3(0.0, 0.0, 0.0);
    view = lookAt(cameraPos, target, radians(cameraRoll));
  } else {
    cameraPos = vec3(-cos(time * 0.1) * 15.0, sin(time * 0.1) * 15.0,
                     sin(time * 0.1) * 15.0);
    vec3 target = vec3(0.0, 0.0, 0.0);
    view = lookAt(cameraPos, target, radians(cameraRoll));
  }

  vec2 uv = gl_FragCoord.xy / resolution.xy - vec2(0.5);
  uv.x *= resolution.x / resolution.y;

  float fov = (useExternalCamera > 0) ? mcFov : 70.0;
  float tanHalfFov = tan(radians(fov) * 0.5);
  vec3 dir = normalize(vec3(uv.x * tanHalfFov * fovScale, uv.y * tanHalfFov * fovScale, 1.0));
  vec3 pos = cameraPos;
  dir = view * dir;

  fragColor = traceColor(pos, dir, view, fov);
}
