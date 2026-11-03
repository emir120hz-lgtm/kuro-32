#version 330
// Kuro: Anubis (GPL-3.0, 4ldenz) sky_theme.fsh uzerinden uyarlandi; uniform blogu duz uniform'lara cevrildi.

uniform vec4 Look;
uniform vec4 Amounts;
uniform vec4 ColorLow;
uniform vec4 ColorHigh;
uniform vec4 ColorBand;
uniform vec4 Zenith;
uniform vec4 Horizon;
uniform vec4 Extra; // x: gunduz miktari (1 gun, 0 gece)

in vec3 skyDirection;

out vec4 fragColor;

const vec3 WHITE_HOT = vec3(1.0, 0.95, 0.88);
const float HOLE_DISTANCE = 32.0;
const float HOLE_CAPTURE = 2.598;
const float DISK_INNER = 3.0;
const float DISK_OUTER = 12.0;

float hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash12(i);
    float b = hash12(i + vec2(1.0, 0.0));
    float c = hash12(i + vec2(0.0, 1.0));
    float d = hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float a = hash13(i);
    float b = hash13(i + vec3(1.0, 0.0, 0.0));
    float c = hash13(i + vec3(0.0, 1.0, 0.0));
    float d = hash13(i + vec3(1.0, 1.0, 0.0));
    float e = hash13(i + vec3(0.0, 0.0, 1.0));
    float g = hash13(i + vec3(1.0, 0.0, 1.0));
    float h = hash13(i + vec3(0.0, 1.0, 1.0));
    float k = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, u.x), mix(c, d, u.x), u.y), mix(mix(e, g, u.x), mix(h, k, u.x), u.y), u.z);
}

float fbm2(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 3; i++) {
        sum += amp * noise2(p);
        p = p * 2.07 + vec2(5.3, 1.7);
        amp *= 0.5;
    }
    return sum;
}

float fbm3(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * noise3(p);
        p = p * 2.02 + vec3(13.1, 7.7, 3.3);
        amp *= 0.5;
    }
    return sum;
}

vec2 turn(vec2 v, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec2(v.x * c - v.y * s, v.x * s + v.y * c);
}

vec3 spin(vec3 v, vec3 axis, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return v * c + cross(axis, v) * s + axis * dot(axis, v) * (1.0 - c);
}

vec3 starField(vec3 dir, float density, float time) {
    if (density <= 0.0) return vec3(0.0);
    vec3 total = vec3(0.0);
    for (int layer = 0; layer < 3; layer++) {
        float scale = 110.0 + 70.0 * float(layer);
        vec3 p = dir * scale + vec3(float(layer) * 31.7);
        float pixel = length(fwidth(p));
        vec3 cell = floor(p);
        vec3 rnd = hash33(cell);
        float present = step(rnd.x, 0.022 * density);
        vec3 center = cell + 0.5 + (hash33(cell + 19.19) - 0.5) * 0.5;
        float dist = length(p - center);
        float bright = pow(rnd.y, 2.5);
        float radius = max(0.05 + 0.10 * bright, pixel * 0.7);
        float shape = 1.0 - smoothstep(radius - pixel, radius + pixel, dist);
        float pixelScale = Amounts.w > 0.0 ? Amounts.w : 1.0;
        float referencePixel = pixel * pixelScale;
        float referenceRadius = max(0.05 + 0.10 * bright, referencePixel * 0.7);
        float footprint = (referenceRadius + referencePixel) / max(radius + pixel, 1.0e-6);
        shape *= footprint * footprint;
        float twinkle = 0.65 + 0.35 * sin(time * (0.8 + 2.4 * rnd.z) + rnd.y * 61.0);
        vec3 tint = mix(vec3(0.72, 0.82, 1.00), vec3(1.00, 0.90, 0.76), rnd.z);
        total += tint * shape * present * twinkle * (0.25 + 1.1 * bright);
    }
    return total;
}

float galaxyBand(vec3 dir, float tightness) {
    vec3 axis = normalize(vec3(0.42, 0.30, 0.86));
    float across = dot(dir, axis);
    return exp(-across * across * tightness) * (0.45 + 0.55 * fbm3(dir * 7.0 + 3.0));
}

vec3 deepSpace(vec3 dir, float time, float nebula) {
    vec3 color = mix(Horizon.rgb * 0.22, Zenith.rgb * 0.45, smoothstep(-0.3, 0.9, dir.y));
    if (nebula > 0.001) {
        vec3 q = dir * 1.8 + vec3(time * 0.006, time * 0.002, -time * 0.004);
        float warp = fbm3(q * 1.6);
        float cloud = fbm3(q + vec3(warp * 1.5));
        float body = smoothstep(0.40, 0.82, cloud);
        float tone = fbm3(q * 2.4 + 9.0);
        vec3 gas = mix(ColorLow.rgb, ColorHigh.rgb, smoothstep(0.32, 0.68, tone));
        float dust = smoothstep(0.50, 0.78, fbm3(q * 3.3 + 4.0));
        color += gas * body * (1.0 - 0.55 * dust) * 0.85 * nebula;
    }
    if (Amounts.z > 0.0 || Amounts.x > 0.0) {
        float band = galaxyBand(dir, 16.0);
        color += ColorBand.rgb * band * 0.5 * Amounts.z;
        color += starField(dir, Amounts.x * (1.0 + 1.5 * band), time);
    }
    return color;
}

vec3 auroraLight(vec3 dir, float time) {
    if (dir.y <= 0.0) return vec3(0.0);
    vec3 sum = vec3(0.0);
    float rise = max(dir.y, 0.03);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec2 spread = dir.xz / rise * 0.6;
    vec2 drift = vec2(time * 0.025, time * 0.01);
    float depth = mix(0.6, 1.4, smoothstep(0.05, 0.45, dir.y));
    float sway = fbm2((spread * (1.0 + 0.5 * depth) + drift) * 0.25 + vec2(time * 0.02, 0.0));
    for (int i = 0; i < 24; i++) {
        float layer = (float(i) + jitter) / 24.0;
        vec2 at = spread * (1.0 + layer * depth) + drift;
        float field = fbm2(vec2(at.x * 0.22, at.y * 0.9) + vec2(sway * 2.2, time * 0.03));
        float fold = pow(1.0 - abs(field * 2.0 - 1.0), 10.0);
        float rays = 0.45 + 0.55 * noise2(vec2(at.x * 1.7, at.y * 0.35) + vec2(time * 0.08, 0.0));
        vec3 tint = mix(ColorLow.rgb, ColorHigh.rgb, layer);
        sum += tint * fold * rays * exp(-layer * 2.6);
    }
    float fade = smoothstep(0.0, 0.18, dir.y) * (1.0 - 0.5 * smoothstep(0.75, 1.0, dir.y));
    return sum * fade * 0.28;
}

vec3 northernSky(vec3 dir, float time) {
    vec3 color = mix(Horizon.rgb * 0.8, Zenith.rgb, smoothstep(-0.1, 0.85, dir.y));
    if (Amounts.z > 0.0) color += ColorBand.rgb * galaxyBand(dir, 18.0) * 0.25 * Amounts.z * smoothstep(-0.05, 0.2, dir.y);
    color += starField(dir, Amounts.x * 0.8, time) * smoothstep(-0.05, 0.25, dir.y);
    if (Amounts.y > 0.0) color += auroraLight(dir, time) * Amounts.y;
    return color;
}

float ringDensity(float r) {
    float body = smoothstep(1.32, 1.40, r) * (1.0 - smoothstep(2.10, 2.24, r));
    float ringlets = 0.55 + 0.45 * noise2(vec2(r * 26.0, 0.5));
    float bands = 0.70 + 0.30 * sin(r * 19.0);
    float gap = smoothstep(0.012, 0.035, abs(r - 1.80));
    return body * ringlets * bands * gap;
}

vec3 ringedPlanet(vec3 dir, float time) {
    vec3 color = deepSpace(dir, time, Amounts.y * 0.6);
    vec3 center = normalize(vec3(0.35, 0.36, -0.86)) * 10.0;
    float radius = 2.4;
    vec3 axis = normalize(vec3(0.2, 0.7, 0.68));
    vec3 light = normalize(vec3(0.61, 0.21, 0.76));
    vec3 pale = mix(vec3(0.93, 0.86, 0.74), ColorBand.rgb, 0.35);
    vec3 deep = mix(vec3(0.52, 0.36, 0.24), ColorLow.rgb, 0.45);
    vec3 air = mix(ColorBand.rgb, ColorLow.rgb, 0.35);

    float along = dot(dir, center);
    float miss = sqrt(max(dot(center, center) - along * along, 0.0));
    float edge = max(fwidth(miss), 1.0e-4);
    float cover = along > 0.0 ? 1.0 - smoothstep(radius - edge, radius + edge, miss) : 0.0;
    float hitAt = along - sqrt(max(radius * radius - miss * miss, 0.0));

    vec3 limb = (dir * along - center) / max(miss, 1.0e-4);
    float glow = along > 0.0 ? exp(-max(miss - radius, 0.0) / (radius * 0.05)) : 0.0;
    color += air * glow * smoothstep(-0.35, 0.55, dot(limb, light)) * 0.45 * (1.0 - cover);

    if (cover > 0.0) {
        vec3 n = normalize(dir * hitAt - center);
        vec3 turned = spin(n, axis, time * 0.02);
        float lat = dot(n, axis);
        float turb = fbm3(turned * 3.2 + 1.7) - 0.5;
        float bands = fbm2(vec2(lat * 7.5 + turb * 0.9, 2.3));
        float storms = fbm3(turned * 6.0 + vec3(lat * 14.0));
        vec3 surface = mix(deep, pale, smoothstep(0.25, 0.75, bands));
        surface = mix(surface, ColorHigh.rgb * 0.75, smoothstep(0.55, 0.85, storms) * 0.35);

        float shade = 1.0;
        float lightUp = dot(light, axis);
        float toPlane = dot(center - dir * hitAt, axis) / lightUp;
        if (toPlane > 0.0) shade -= 0.8 * ringDensity(length(dir * hitAt + light * toPlane - center) / radius);
        float sun = smoothstep(-0.12, 0.55, dot(n, light)) * shade;
        float view = max(dot(n, -dir), 0.0);
        vec3 body = surface * (0.03 + 0.97 * sun) * (0.6 + 0.4 * view);
        body += air * pow(1.0 - view, 3.0) * sun * 0.5;
        color = mix(color, body, cover);
    }

    float facing = dot(dir, axis);
    float ringAt = abs(facing) > 1.0e-4 ? dot(center, axis) / facing : -1.0;
    if (ringAt > 0.0) {
        vec3 spot = dir * ringAt;
        float r = length(spot - center) / radius;
        float density = ringDensity(r);
        if (density > 0.0) {
            vec3 toCenter = center - spot;
            float ahead = dot(toCenter, light);
            float gap = sqrt(max(dot(toCenter, toCenter) - ahead * ahead, 0.0));
            float lit = ahead > 0.0 ? smoothstep(radius * 0.96, radius * 1.04, gap) : 1.0;
            vec3 ring = mix(pale, deep, 0.35 + 0.35 * sin(r * 7.0)) * (0.08 + 0.92 * lit);
            float shown = cover > 0.0 && ringAt > hitAt ? 1.0 - cover : 1.0;
            color = mix(color, ring, density * 0.9 * shown);
        }
    }
    return color;
}

vec3 blackHole(vec3 dir, float time) {
    vec3 ahead = normalize(vec3(-0.30, 0.50, 0.81));
    vec3 right = normalize(cross(ahead, vec3(0.0, 1.0, 0.0)));
    vec3 up = cross(right, ahead);
    vec3 local = vec3(dot(dir, right), dot(dir, up), dot(dir, ahead));
    float off = length(local.xy);
    float angle = atan(off, local.z);
    vec2 outward = local.xy / max(off, 1.0e-6);
    float impact = HOLE_DISTANCE * off;
    float edge = max(fwidth(impact), 1.0e-4);
    float open = local.z > 0.0 ? smoothstep(HOLE_CAPTURE - edge, HOLE_CAPTURE + edge, impact) : 1.0;

    float seen = angle - 2.0 / max(impact, HOLE_CAPTURE) * (1.0 - smoothstep(0.9, 1.6, angle));
    vec3 lensed = vec3(outward * sin(seen), cos(seen));
    vec3 glow = vec3(0.0);
    float cover = 0.0;
    if (angle < 0.62) {
        vec3 normal = normalize(vec3(0.17, 0.97, -0.17));
        vec3 diskX = normalize(cross(normal, vec3(0.0, 0.0, 1.0)));
        vec3 diskY = cross(normal, diskX);
        vec3 pos = vec3(0.0, 0.0, -HOLE_DISTANCE);
        vec3 vel = local;
        vec3 momentum = cross(pos, vel);
        float h2 = dot(momentum, momentum);
        float side = dot(pos, normal);
        for (int i = 0; i < 110; i++) {
            float r = length(pos);
            if (r < 1.0 || cover > 0.97 || (r > HOLE_DISTANCE * 1.5 && dot(pos, vel) > 0.0)) break;
            float dt = clamp(0.07 * r, 0.02, 3.0);
            vel -= 1.5 * h2 * pos / (r * r * r * r * r) * dt;
            vec3 next = pos + vel * dt;
            float nextSide = dot(next, normal);
            if (side * nextSide < 0.0) {
                vec3 hit = mix(pos, next, side / (side - nextSide));
                float rr = length(hit);
                if (rr > DISK_INNER * 0.9 && rr < DISK_OUTER) {
                    vec2 around = vec2(dot(hit, diskX), dot(hit, diskY)) / rr;
                    float orbit = time * 0.5 * pow(DISK_INNER / rr, 1.5);
                    float gas = fbm3(vec3(turn(around, -orbit) * 2.6, rr * 0.8));
                    float profile = smoothstep(DISK_INNER * 0.95, DISK_INNER * 1.2, rr)
                            * (1.0 - smoothstep(DISK_OUTER * 0.5, DISK_OUTER, rr));
                    float heat = 1.0 - smoothstep(DISK_INNER, DISK_OUTER * 0.8, rr);
                    float beam = 1.0 - 0.45 * dot(cross(normal, hit) / rr, normalize(vel)) * sqrt(DISK_INNER / rr);
                    vec3 tint = mix(mix(ColorHigh.rgb, ColorLow.rgb, heat), WHITE_HOT, heat * heat * 0.6);
                    float alpha = profile * clamp(0.35 + 0.9 * gas, 0.0, 1.0) * 0.9;
                    glow += (1.0 - cover) * alpha * tint * (0.5 + 1.5 * gas) * beam * beam * pow(DISK_INNER / rr, 1.3) * 2.0;
                    cover += (1.0 - cover) * alpha;
                }
            }
            side = nextSide;
            pos = next;
        }
        lensed = normalize(mix(normalize(vel), lensed, smoothstep(0.45, 0.62, angle)));
    }
    vec3 source = right * lensed.x + up * lensed.y + ahead * lensed.z;
    return deepSpace(source, time, Amounts.y * 0.6) * open * (1.0 - cover) + glow;
}

void main() {
    vec3 dir = normalize(skyDirection);
    float time = Look.z;
    vec3 color;
if (Look.x < 0.5) color = deepSpace(dir, time, Amounts.y);
    else if (Look.x < 1.5) color = northernSky(dir, time);
    else if (Look.x < 2.5) color = ringedPlanet(dir, time);
    else color = blackHole(dir, time);
    color = 1.0 - exp(-color * Look.w * 1.4);
    // Gunduz horizonda vanilla sis rengine karis: arazi sisi ile gokyuzu arasinda cizgi olmasin
    float fade = mix(1.0, smoothstep(0.0, 0.3, dir.y), Extra.x);
    fragColor = vec4(color, Look.y * fade);
}
