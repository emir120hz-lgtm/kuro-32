#version 330

// Tam ekran ucgen (VBO yok): gl_VertexID ile uretilir. z = 0.99999 -> sadece bos (derinlik 1.0) piksellere cizilir.
uniform mat3 InvView;
uniform vec2 TanHalf;

out vec3 skyDirection;
out vec2 uv;

void main() {
    vec2 p = vec2(gl_VertexID == 1 ? 3.0 : -1.0, gl_VertexID == 2 ? 3.0 : -1.0);
    gl_Position = vec4(p, 0.99999, 1.0);
    skyDirection = InvView * vec3(p * TanHalf, -1.0);
    uv = p * 0.5 + 0.5;
}
