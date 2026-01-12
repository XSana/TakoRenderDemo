package moe.takochan.takorender.demo.client.render;

import java.nio.FloatBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import moe.takochan.takorender.demo.TakoRenderDemoMod;
import moe.takochan.takorender.demo.client.shader.ShaderHelper;
import moe.takochan.takorender.demo.tile.TileEntityBlackHole;

public class TileEntityBlackHoleRenderer extends TileEntitySpecialRenderer {

    // Shader programs
    private int blackholeShader = -1;
    private int brightnessShader = -1;
    private int downsampleShader = -1;
    private int upsampleShader = -1;
    private int compositeShader = -1;
    private int tonemappingShader = -1;
    private int passthroughShader = -1;

    // 纹理
    private int galaxyCubemap = -1;
    private int colorMapTexture = -1;

    // FBO和纹理 - 后处理管线
    private int fboBlackhole = -1;
    private int texBlackhole = -1;

    private int fboBrightness = -1;
    private int texBrightness = -1;

    private static final int MAX_BLOOM_ITER = 8;
    private int[] fboDownsampled = new int[MAX_BLOOM_ITER];
    private int[] texDownsampled = new int[MAX_BLOOM_ITER];
    private int[] fboUpsampled = new int[MAX_BLOOM_ITER];
    private int[] texUpsampled = new int[MAX_BLOOM_ITER];

    private int fboBloomFinal = -1;
    private int texBloomFinal = -1;

    private int fboTonemapped = -1;
    private int texTonemapped = -1;

    private static final float RENDER_HEIGHT_OFFSET = 3.0f;
    private static final float BLACKHOLE_SCALE = 1.5f; // 黑洞尺寸缩放 (值越大引力场越小)
    private static final int BLOOM_ITERATIONS = 6;
    private static final float BLOOM_STRENGTH = 0.3f; // 增强bloom效果

    private boolean initialized = false;
    private long startTime = System.currentTimeMillis();

    private int renderWidth = 0;
    private int renderHeight = 0;

    @Override
    public void renderTileEntityAt(TileEntity te, double x, double y, double z, float partialTicks) {
        if (!(te instanceof TileEntityBlackHole)) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.thePlayer;
        if (player == null) {
            return;
        }

        // 检查分辨率变化
        if (mc.displayWidth != renderWidth || mc.displayHeight != renderHeight) {
            renderWidth = mc.displayWidth;
            renderHeight = mc.displayHeight;
            initialized = false;
        }

        if (!initialized) {
            init(renderWidth, renderHeight);
        }

        if (blackholeShader <= 0) {
            return;
        }

        float time = (System.currentTimeMillis() - startTime) / 1000.0f;

        // 黑洞中心在世界坐标中的位置
        double blackHoleX = te.xCoord + 0.5;
        double blackHoleY = te.yCoord + RENDER_HEIGHT_OFFSET;
        double blackHoleZ = te.zCoord + 0.5;

        // 计算相机世界坐标
        double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks + player.getEyeHeight();
        double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        // 相机相对于黑洞中心的位置
        float relX = (float) (camX - blackHoleX) / BLACKHOLE_SCALE;
        float relY = (float) (camY - blackHoleY) / BLACKHOLE_SCALE;
        float relZ = (float) (camZ - blackHoleZ) / BLACKHOLE_SCALE;

        // 相机方向
        float yaw = player.prevRotationYaw + (player.rotationYaw - player.prevRotationYaw) * partialTicks;
        float pitch = player.prevRotationPitch + (player.rotationPitch - player.prevRotationPitch) * partialTicks;

        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        // 前方向量 (MC坐标系)
        float dirX = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        float dirY = (float) (-Math.sin(pitchRad));
        float dirZ = (float) (Math.cos(yawRad) * Math.cos(pitchRad));

        // 使用cross product正确计算up向量
        // 世界up向量
        float worldUpX = 0.0f, worldUpY = 1.0f, worldUpZ = 0.0f;

        // right = forward × worldUp (cross product)
        float rightX = dirY * worldUpZ - dirZ * worldUpY;
        float rightY = dirZ * worldUpX - dirX * worldUpZ;
        float rightZ = dirX * worldUpY - dirY * worldUpX;

        // 归一化right向量
        float rightLen = (float) Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (rightLen > 0.0001f) {
            rightX /= rightLen;
            rightY /= rightLen;
            rightZ /= rightLen;
        } else {
            // 当向正上或正下看时，使用备用right向量
            rightX = 1.0f;
            rightY = 0.0f;
            rightZ = 0.0f;
        }

        // up = right × forward (cross product)
        float upX = rightY * dirZ - rightZ * dirY;
        float upY = rightZ * dirX - rightX * dirZ;
        float upZ = rightX * dirY - rightY * dirX;

        // 保存MC的FBO
        int mcFBO = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        // 获取MC framebuffer的颜色纹理
        int mcBackgroundTexture = mc.getFramebuffer().framebufferTexture;

        // 保存OpenGL状态
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        // === 后处理管线 ===

        // 1. 渲染黑洞到FBO (传入MC背景纹理)
        renderBlackhole(
            time,
            relX,
            relY,
            relZ,
            dirX,
            dirY,
            dirZ,
            upX,
            upY,
            upZ,
            mc.gameSettings.fovSetting,
            mcBackgroundTexture);

        // 2. 提取高亮度像素
        renderBrightnessPass();

        // 3. Bloom降采样
        for (int level = 0; level < BLOOM_ITERATIONS; level++) {
            renderDownsample(level);
        }

        // 4. Bloom上采样
        for (int level = BLOOM_ITERATIONS - 1; level >= 0; level--) {
            renderUpsample(level);
        }

        // 5. 合成
        renderComposite();

        // 6. 色调映射
        renderTonemapping();

        // 恢复MC的FBO
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mcFBO);

        // 7. 最终渲染到屏幕
        renderFinalPass(mc);

        GL11.glPopAttrib();
    }

    private void init(int width, int height) {
        // 编译所有shader
        blackholeShader = ShaderHelper.createShaderProgram("simple.vert", "blackhole_main.frag");
        brightnessShader = ShaderHelper.createShaderProgram("simple.vert", "bloom_brightness_pass.frag");
        downsampleShader = ShaderHelper.createShaderProgram("simple.vert", "bloom_downsample.frag");
        upsampleShader = ShaderHelper.createShaderProgram("simple.vert", "bloom_upsample.frag");
        compositeShader = ShaderHelper.createShaderProgram("simple.vert", "bloom_composite.frag");
        tonemappingShader = ShaderHelper.createShaderProgram("simple.vert", "tonemapping.frag");
        passthroughShader = ShaderHelper.createShaderProgram("simple.vert", "passthrough.frag");

        if (blackholeShader <= 0) {
            TakoRenderDemoMod.LOG.error("Failed to create blackhole shader!");
            initialized = true;
            return;
        }

        // 创建FBO和纹理
        // 黑洞渲染
        texBlackhole = createHDRTexture(width, height);
        fboBlackhole = createFBO(texBlackhole);

        // 亮度提取
        texBrightness = createHDRTexture(width, height);
        fboBrightness = createFBO(texBrightness);

        // Bloom降采样/上采样链
        for (int i = 0; i < MAX_BLOOM_ITER; i++) {
            int w = width >> (i + 1);
            int h = height >> (i + 1);
            if (w < 1) w = 1;
            if (h < 1) h = 1;

            texDownsampled[i] = createHDRTexture(w, h);
            fboDownsampled[i] = createFBO(texDownsampled[i]);

            int uw = width >> i;
            int uh = height >> i;
            if (uw < 1) uw = 1;
            if (uh < 1) uh = 1;
            texUpsampled[i] = createHDRTexture(uw, uh);
            fboUpsampled[i] = createFBO(texUpsampled[i]);
        }

        // Bloom合成
        texBloomFinal = createHDRTexture(width, height);
        fboBloomFinal = createFBO(texBloomFinal);

        // 色调映射
        texTonemapped = createHDRTexture(width, height);
        fboTonemapped = createFBO(texTonemapped);

        // 创建默认纹理
        galaxyCubemap = createDefaultCubemap();
        colorMapTexture = createDefaultColorMap();

        RenderHelper.initQuadVAO();
        initialized = true;

        TakoRenderDemoMod.LOG.info("Bloom pipeline initialized at " + width + "x" + height);
    }

    private void renderBlackhole(float time, float camX, float camY, float camZ, float dirX, float dirY, float dirZ,
        float upX, float upY, float upZ, float fov, int mcBackgroundTexture) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboBlackhole);
        GL11.glViewport(0, 0, renderWidth, renderHeight);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL20.glUseProgram(blackholeShader);

        ShaderHelper.setUniform2f(blackholeShader, "resolution", renderWidth, renderHeight);
        ShaderHelper.setUniform1f(blackholeShader, "time", time);

        ShaderHelper.setUniform1i(blackholeShader, "useExternalCamera", 1);
        ShaderHelper.setUniform3f(blackholeShader, "mcCameraPos", camX, camY, camZ);
        ShaderHelper.setUniform3f(blackholeShader, "mcCameraDir", dirX, dirY, dirZ);
        ShaderHelper.setUniform3f(blackholeShader, "mcCameraUp", upX, upY, upZ);
        ShaderHelper.setUniform1f(blackholeShader, "mcFov", fov);

        ShaderHelper.setUniform1i(blackholeShader, "transparentBackground", 1);
        ShaderHelper.setUniform1f(blackholeShader, "gravatationalLensing", 1.0f);
        ShaderHelper.setUniform1f(blackholeShader, "renderBlackHole", 1.0f);
        ShaderHelper.setUniform1f(blackholeShader, "fovScale", 1.0f);

        ShaderHelper.setUniform1f(blackholeShader, "adiskEnabled", 1.0f);
        ShaderHelper.setUniform1f(blackholeShader, "adiskParticle", 1.0f);
        ShaderHelper.setUniform1f(blackholeShader, "adiskHeight", 0.55f); // 原始值
        ShaderHelper.setUniform1f(blackholeShader, "adiskLit", 0.25f); // 原始值
        ShaderHelper.setUniform1f(blackholeShader, "adiskDensityV", 2.0f); // 原始值
        ShaderHelper.setUniform1f(blackholeShader, "adiskDensityH", 4.0f); // 原始值 - 关键！
        ShaderHelper.setUniform1f(blackholeShader, "adiskNoiseScale", 0.8f); // 原始值
        ShaderHelper.setUniform1f(blackholeShader, "adiskNoiseLOD", 5.0f);
        ShaderHelper.setUniform1f(blackholeShader, "adiskSpeed", 0.5f);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, galaxyCubemap);
        ShaderHelper.setUniform1i(blackholeShader, "galaxy", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorMapTexture);
        ShaderHelper.setUniform1i(blackholeShader, "colorMap", 1);

        // 绑定MC背景纹理
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mcBackgroundTexture);
        ShaderHelper.setUniform1i(blackholeShader, "mcBackground", 2);

        RenderHelper.renderQuad();
        GL20.glUseProgram(0);
    }

    private void renderBrightnessPass() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboBrightness);
        GL11.glViewport(0, 0, renderWidth, renderHeight);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL20.glUseProgram(brightnessShader);
        ShaderHelper.setUniform2f(brightnessShader, "resolution", renderWidth, renderHeight);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texBlackhole);
        ShaderHelper.setUniform1i(brightnessShader, "texture0", 0);

        RenderHelper.renderQuad();
        GL20.glUseProgram(0);
    }

    private void renderDownsample(int level) {
        int w = renderWidth >> (level + 1);
        int h = renderHeight >> (level + 1);
        if (w < 1) w = 1;
        if (h < 1) h = 1;

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboDownsampled[level]);
        GL11.glViewport(0, 0, w, h);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL20.glUseProgram(downsampleShader);
        ShaderHelper.setUniform2f(downsampleShader, "resolution", w, h);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, level == 0 ? texBrightness : texDownsampled[level - 1]);
        ShaderHelper.setUniform1i(downsampleShader, "texture0", 0);

        RenderHelper.renderQuad();
        GL20.glUseProgram(0);
    }

    private void renderUpsample(int level) {
        int w = renderWidth >> level;
        int h = renderHeight >> level;
        if (w < 1) w = 1;
        if (h < 1) h = 1;

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboUpsampled[level]);
        GL11.glViewport(0, 0, w, h);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL20.glUseProgram(upsampleShader);
        ShaderHelper.setUniform2f(upsampleShader, "resolution", w, h);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(
            GL11.GL_TEXTURE_2D,
            level == BLOOM_ITERATIONS - 1 ? texDownsampled[level] : texUpsampled[level + 1]);
        ShaderHelper.setUniform1i(upsampleShader, "texture0", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, level == 0 ? texBrightness : texDownsampled[level - 1]);
        ShaderHelper.setUniform1i(upsampleShader, "texture1", 1);

        RenderHelper.renderQuad();
        GL20.glUseProgram(0);
    }

    private void renderComposite() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboBloomFinal);
        GL11.glViewport(0, 0, renderWidth, renderHeight);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL20.glUseProgram(compositeShader);
        ShaderHelper.setUniform2f(compositeShader, "resolution", renderWidth, renderHeight);
        ShaderHelper.setUniform1f(compositeShader, "tone", 1.0f);
        ShaderHelper.setUniform1f(compositeShader, "bloomStrength", BLOOM_STRENGTH);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texBlackhole);
        ShaderHelper.setUniform1i(compositeShader, "texture0", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texUpsampled[0]);
        ShaderHelper.setUniform1i(compositeShader, "texture1", 1);

        RenderHelper.renderQuad();
        GL20.glUseProgram(0);
    }

    private void renderTonemapping() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboTonemapped);
        GL11.glViewport(0, 0, renderWidth, renderHeight);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL20.glUseProgram(tonemappingShader);
        ShaderHelper.setUniform2f(tonemappingShader, "resolution", renderWidth, renderHeight);
        ShaderHelper.setUniform1f(tonemappingShader, "gamma", 2.5f); // 原始值
        ShaderHelper.setUniform1f(tonemappingShader, "tonemappingEnabled", 1.0f);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texBloomFinal);
        ShaderHelper.setUniform1i(tonemappingShader, "texture0", 0);

        RenderHelper.renderQuad();
        GL20.glUseProgram(0);
    }

    private void renderFinalPass(Minecraft mc) {
        GL11.glPushMatrix();

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(-1, 1, -1, 1, -1, 1);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);

        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);

        GL20.glUseProgram(passthroughShader);
        ShaderHelper.setUniform2f(passthroughShader, "resolution", renderWidth, renderHeight);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texTonemapped);
        ShaderHelper.setUniform1i(passthroughShader, "texture0", 0);

        RenderHelper.renderQuad();

        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();

        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    private int createHDRTexture(int width, int height) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL30.GL_RGBA16F,
            width,
            height,
            0,
            GL11.GL_RGBA,
            GL11.GL_FLOAT,
            (FloatBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }

    private int createFBO(int colorTexture) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D,
            colorTexture,
            0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            TakoRenderDemoMod.LOG.error("FBO not complete: " + status);
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return fbo;
    }

    private int createDefaultCubemap() {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, texture);

        int size = 64;
        java.nio.ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(size * size * 3);

        for (int face = 0; face < 6; face++) {
            buffer.clear();
            for (int j = 0; j < size; j++) {
                for (int i = 0; i < size; i++) {
                    float brightness = (float) Math.random() * 0.1f;
                    if (Math.random() > 0.995) {
                        brightness = 0.8f + (float) Math.random() * 0.2f;
                    }
                    buffer.put((byte) (brightness * 50));
                    buffer.put((byte) (brightness * 50));
                    buffer.put((byte) (brightness * 100 + 20));
                }
            }
            buffer.flip();
            GL11.glTexImage2D(
                GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face,
                0,
                GL11.GL_RGB,
                size,
                size,
                0,
                GL11.GL_RGB,
                GL11.GL_UNSIGNED_BYTE,
                buffer);
        }

        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, 0x812F);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, 0x812F);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, 0x8072, 0x812F);

        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
        return texture;
    }

    private int createDefaultColorMap() {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);

        int width = 256;
        int height = 1;
        java.nio.ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(width * height * 3);

        for (int i = 0; i < width; i++) {
            float t = i / (float) width;
            buffer.put((byte) (255));
            buffer.put((byte) (100 + t * 155));
            buffer.put((byte) (t * 200));
        }
        buffer.flip();

        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D,
            0,
            GL11.GL_RGB,
            width,
            height,
            0,
            GL11.GL_RGB,
            GL11.GL_UNSIGNED_BYTE,
            buffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, 0x812F);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, 0x812F);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return texture;
    }
}
