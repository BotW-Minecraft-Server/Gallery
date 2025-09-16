package link.botwmcs.gallery.util;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Tickable;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.io.IOException;

public class AnimatedDynamicTexture extends AbstractTexture implements Tickable, AutoCloseable {
    private final NativeImage[] frames;
    private final int[] delaysMs; // 与 frames 同长度，单位毫秒，最小 20ms
    private final boolean clampWrap;  // 是否使用 CLAMP_TO_EDGE
    private int frameIndex = 0;
    private int accumMs = 0;
    private final int width, height;

    public AnimatedDynamicTexture(NativeImage[] frames, int[] delaysMs, boolean clampWrap) {
        if (frames == null || frames.length == 0) {
            throw new IllegalArgumentException("frames must not be empty");
        }
        this.frames = frames;
        this.delaysMs = normalizeDelays(delaysMs, frames.length);
        this.width = frames[0].getWidth();
        this.height = frames[0].getHeight();
        this.clampWrap = clampWrap;

        // 分配并初始化 GL 纹理
        this.bind();
        // 注意：1.21 的 TextureUtil 在 blaze3d 平台包下
        TextureUtil.prepareImage(this.getId(), 0, width, height);
        applyWrap();        // 这里设置 wrap 模式
        uploadCurrent();    // 先上传首帧
    }

    private static int[] normalizeDelays(int[] delays, int n) {
        int[] out = new int[n];
        if (delays == null || delays.length != n) {
            java.util.Arrays.fill(out, 100);
            return out;
        }
        for (int i = 0; i < n; i++) out[i] = Math.max(20, delays[i]);
        return out;
    }

    /** 设置纹理的 S/T wrap：CLAMP_TO_EDGE 或 REPEAT */
    private void applyWrap() {
        this.bind(); // 先绑定到当前纹理单元
        int wrap = clampWrap ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT;
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, wrap);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, wrap);
    }

    private void uploadCurrent() {
        this.bind();
        // 1.21 的 NativeImage 简化 upload 重载通常为：upload(level, x, y, doNotClose)
        frames[frameIndex].upload(0, 0, 0, false);
    }

    @Override
    public void tick() {
        // 以 50ms 粒度推进（≈ 1 tick）
        accumMs += 50;
        int dur = delaysMs[frameIndex];
        if (accumMs >= dur) {
            accumMs -= dur;
            frameIndex = (frameIndex + 1) % frames.length;
            uploadCurrent();
        }
    }

    @Override
    public void close() {
        for (NativeImage img : frames) {
            if (img != null) img.close();
        }
        this.releaseId();
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        // 资源重载时，TextureManager 会重新调用这里：
        // 1) 重新分配/准备纹理存储
        this.bind();
        TextureUtil.prepareImage(this.getId(), 0, width, height);
        // 2) 重新设置 wrap（如果你需要 CLAMP_TO_EDGE）
        int wrap = clampWrap ? GL12.GL_CLAMP_TO_EDGE : GL11.GL_REPEAT;
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, wrap);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, wrap);
        // 3) 重新上传当前帧
        uploadCurrent();
    }



}
