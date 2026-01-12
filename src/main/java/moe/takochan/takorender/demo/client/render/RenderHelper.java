package moe.takochan.takorender.demo.client.render;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class RenderHelper {

    private static int quadVAO = -1;
    private static int quadVBO = -1;

    private static final float[] QUAD_VERTICES = {
        // positions (x, y, z)
        -1.0f, -1.0f, 0.0f, 1.0f, -1.0f, 0.0f, 1.0f, 1.0f, 0.0f, -1.0f, -1.0f, 0.0f, 1.0f, 1.0f, 0.0f, -1.0f, 1.0f,
        0.0f };

    public static void initQuadVAO() {
        if (quadVAO != -1) {
            return;
        }

        quadVAO = GL30.glGenVertexArrays();
        quadVBO = GL15.glGenBuffers();

        GL30.glBindVertexArray(quadVAO);

        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(QUAD_VERTICES.length);
        vertexBuffer.put(QUAD_VERTICES);
        vertexBuffer.flip();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * 4, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    public static void renderQuad() {
        if (quadVAO == -1) {
            initQuadVAO();
        }
        GL30.glBindVertexArray(quadVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
        GL30.glBindVertexArray(0);
    }

    public static int createFramebuffer(int width, int height) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        int colorTexture = createColorTexture(width, height, true);
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D,
            colorTexture,
            0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer not complete: " + status);
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return fbo;
    }

    public static int createColorTexture(int width, int height, boolean hdr) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        if (hdr) {
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL30.GL_RGB16F,
                width,
                height,
                0,
                GL11.GL_RGB,
                GL11.GL_FLOAT,
                (FloatBuffer) null);
        } else {
            GL11.glTexImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                GL11.GL_RGB,
                width,
                height,
                0,
                GL11.GL_RGB,
                GL11.GL_UNSIGNED_BYTE,
                (java.nio.ByteBuffer) null);
        }

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    public static int createCubemap(int size) {
        int texture = GL11.glGenTextures();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, texture);

        for (int i = 0; i < 6; i++) {
            GL11.glTexImage2D(
                GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                0,
                GL11.GL_RGB,
                size,
                size,
                0,
                GL11.GL_RGB,
                GL11.GL_UNSIGNED_BYTE,
                (java.nio.ByteBuffer) null);
        }

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);

        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
        return texture;
    }

    public static void cleanup() {
        if (quadVAO != -1) {
            GL30.glDeleteVertexArrays(quadVAO);
            quadVAO = -1;
        }
        if (quadVBO != -1) {
            GL15.glDeleteBuffers(quadVBO);
            quadVBO = -1;
        }
    }
}

class GL12 {

    public static final int GL_CLAMP_TO_EDGE = 0x812F;
    public static final int GL_TEXTURE_WRAP_R = 0x8072;
}
