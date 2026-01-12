package moe.takochan.takorender.demo.client.shader;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import moe.takochan.takorender.demo.TakoRenderDemoMod;

public class ShaderHelper {

    private static final Map<String, Integer> shaderProgramCache = new HashMap<>();

    public static int createShaderProgram(String vertexShaderPath, String fragmentShaderPath) {
        String key = vertexShaderPath + "|" + fragmentShaderPath;
        if (shaderProgramCache.containsKey(key)) {
            return shaderProgramCache.get(key);
        }

        int vertexShader = compileShader(vertexShaderPath, GL20.GL_VERTEX_SHADER);
        int fragmentShader = compileShader(fragmentShaderPath, GL20.GL_FRAGMENT_SHADER);

        if (vertexShader == 0 || fragmentShader == 0) {
            return 0;
        }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);

        IntBuffer linkStatus = BufferUtils.createIntBuffer(1);
        GL20.glGetProgram(program, GL20.GL_LINK_STATUS, linkStatus);
        if (linkStatus.get(0) == GL11.GL_FALSE) {
            int logLength = GL20.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH);
            String log = GL20.glGetProgramInfoLog(program, logLength);
            TakoRenderDemoMod.LOG.error("Shader program link failed: " + log);
            GL20.glDeleteProgram(program);
            return 0;
        }

        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        shaderProgramCache.put(key, program);
        return program;
    }

    private static int compileShader(String shaderPath, int shaderType) {
        String source = readShaderFile(shaderPath);
        if (source == null || source.isEmpty()) {
            TakoRenderDemoMod.LOG.error("Failed to read shader file: " + shaderPath);
            return 0;
        }

        int shader = GL20.glCreateShader(shaderType);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        IntBuffer compileStatus = BufferUtils.createIntBuffer(1);
        GL20.glGetShader(shader, GL20.GL_COMPILE_STATUS, compileStatus);
        if (compileStatus.get(0) == GL11.GL_FALSE) {
            int logLength = GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH);
            String log = GL20.glGetShaderInfoLog(shader, logLength);
            TakoRenderDemoMod.LOG.error("Shader compile failed (" + shaderPath + "): " + log);
            GL20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    private static String readShaderFile(String path) {
        try {
            InputStream inputStream = ShaderHelper.class
                .getResourceAsStream("/assets/" + TakoRenderDemoMod.MODID + "/shaders/" + path);
            if (inputStream == null) {
                TakoRenderDemoMod.LOG.error("Shader file not found: " + path);
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line)
                    .append("\n");
            }
            reader.close();
            return builder.toString();
        } catch (Exception e) {
            TakoRenderDemoMod.LOG.error("Error reading shader file: " + path, e);
            return null;
        }
    }

    public static void setUniform1f(int program, String name, float value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }

    public static void setUniform2f(int program, String name, float x, float y) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }

    public static void setUniform3f(int program, String name, float x, float y, float z) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform3f(location, x, y, z);
        }
    }

    public static void setUniform1i(int program, String name, int value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }

    public static void deleteAllShaders() {
        for (int program : shaderProgramCache.values()) {
            GL20.glDeleteProgram(program);
        }
        shaderProgramCache.clear();
    }
}
