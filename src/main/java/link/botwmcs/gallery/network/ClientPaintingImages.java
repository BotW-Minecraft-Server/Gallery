package link.botwmcs.gallery.network;

import com.mojang.blaze3d.platform.NativeImage;
import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.entity.Painting;
import link.botwmcs.gallery.util.AnimatedDynamicTexture;
import link.botwmcs.gallery.util.GifDecoder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ClientPaintingImages {
    public interface ImageSource {
        /** 基于 id 返回图片字节；可从磁盘缓存或网络抓取。异常由上层吞并并回退占位。 */
        byte[] load(ResourceLocation id) throws Exception;
        /** 可选：返回扩展名用于命名（png/gif/webp）；可返回 null。 */
        default String ext(ResourceLocation id) { return "png"; }
    }

    public static final ConcurrentMap<ResourceLocation, EnumMap<Painting.Size, ResourceLocation>> TEXTURES = new ConcurrentHashMap<>();
    public static final ConcurrentMap<ResourceLocation, ImageMeta> METAS = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);

    public static final class ImageMeta {
        public final int pixelW, pixelH;
        public final boolean animated;
        public ImageMeta(int w, int h, boolean anim) { this.pixelW = w; this.pixelH = h; this.animated = anim; }
    }

    /** 渲染器调用入口：取某个 LOD 的纹理；若未准备则触发异步准备并返回占位图。 */
    public static ResourceLocation getImageIdentifier(ResourceLocation id, Painting.Size size, ImageSource source) {
        EnumMap<Painting.Size, ResourceLocation> m = TEXTURES.get(id);
        if (m != null) {
            ResourceLocation rl = m.get(size);
            if (rl != null) return rl;
        }
        ensureLocalTextures(id, source); // 异步准备
        return Painting.DEFAULT_IDENTIFIER;
    }

    /** 触发异步加载/解码/注册纹理。已存在则跳过。 */
    public static void ensureLocalTextures(ResourceLocation id, ImageSource source) {
        if (TEXTURES.containsKey(id)) return;
        POOL.submit(() -> {
            try {
                byte[] bytes = source.load(id);
                if (bytes == null || bytes.length == 0) return;

                Decoded decoded = decode(bytes);
                METAS.put(id, new ImageMeta(decoded.width, decoded.height, decoded.animated));

                EnumMap<Painting.Size, ResourceLocation> out = new EnumMap<>(Painting.Size.class);
                registerAllSizes(id, decoded, out);
                TEXTURES.put(id, out);
            } catch (Throwable t) {
                // 失败时保持使用占位纹理
            }
        });
    }

    /* ================= 解码部分 ================= */

    private static final class Decoded {
        final boolean animated;
        final BufferedImage full;
        final List<BufferedImage> frames;
        final int[] delays;
        final int width, height;
        private Decoded(BufferedImage full) {
            this.animated = false; this.full = full; this.frames = null; this.delays = null;
            this.width = full.getWidth(); this.height = full.getHeight();
        }
        private Decoded(List<BufferedImage> frames, int[] delays) {
            this.animated = true; this.frames = frames; this.delays = delays;
            this.full = frames.get(0);
            this.width = full.getWidth(); this.height = full.getHeight();
        }
    }

    /** 粗略按文件头判断 GIF；其它交给 ImageIO（含 PNG/JPEG/WebP 插件）。 */
    private static Decoded decode(byte[] bytes) throws Exception {
        if (isGif(bytes)) {
            GifDecoder.Result r = GifDecoder.decode(bytes);
            return new Decoded(r.frames, r.delaysMs);
        }
        // 非 GIF：尝试用 ImageIO 读静态首帧（若安装了 WebP 插件也能读）
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        if (img == null) throw new IllegalStateException("Unsupported image format");
        return new Decoded(img);
    }

    private static boolean isGif(byte[] b) {
        return b.length >= 6 &&
                b[0]=='G' && b[1]=='I' && b[2]=='F' &&
                b[3]=='8' && (b[4]=='7' || b[4]=='9') && b[5]=='a';
    }

    /* ================= 注册/LOD 部分 ================= */

    private static void registerAllSizes(ResourceLocation id, Decoded decoded, EnumMap<Painting.Size, ResourceLocation> out) {
        if (decoded.animated) {
            out.put(Painting.Size.FULL,      registerAnimated(id, decoded.frames, decoded.delays, 1.0f));
            out.put(Painting.Size.HALF,      registerAnimated(id, decoded.frames, decoded.delays, 0.5f));
            out.put(Painting.Size.QUARTER,   registerAnimated(id, decoded.frames, decoded.delays, 0.25f));
            out.put(Painting.Size.EIGHTH,    registerAnimated(id, decoded.frames, decoded.delays, 0.125f));
            out.put(Painting.Size.THUMBNAIL, registerAnimated(id, decoded.frames, decoded.delays, thumbScale(decoded.width, decoded.height)));
        } else {
            out.put(Painting.Size.FULL,      registerStatic(id, decoded.full));
            out.put(Painting.Size.HALF,      registerStatic(id, scale(decoded.full, 0.5f)));
            out.put(Painting.Size.QUARTER,   registerStatic(id, scale(decoded.full, 0.25f)));
            out.put(Painting.Size.EIGHTH,    registerStatic(id, scale(decoded.full, 0.125f)));
            out.put(Painting.Size.THUMBNAIL, registerStatic(id, scaleToThumb(decoded.full)));
        }
    }

    /** 注册静态图为 DynamicTexture 并返回 RL。 */
    private static ResourceLocation registerStatic(ResourceLocation id, BufferedImage img) {
        NativeImage ni = toNative(img);
        ResourceLocation rl = Gallery.locate("textures/runtime/" + hashFrom(id) + "_static_" + img.getWidth() + "x" + img.getHeight());
        DynamicTexture dyn = new DynamicTexture(ni);
        Minecraft.getInstance().getTextureManager().register(rl, dyn); // void
        return rl;

    }

    /** 将多帧缩放并注册为 AnimatedDynamicTexture。 */
    private static ResourceLocation registerAnimated(ResourceLocation id, List<BufferedImage> frames, int[] delays, float scale) {
        NativeImage[] nis = new NativeImage[frames.size()];
        for (int i = 0; i < frames.size(); i++) {
            BufferedImage f = frames.get(i);
            nis[i] = toNative(scale == 1.0f ? f : scale(f, scale));
        }
        ResourceLocation rl = Gallery.locate("textures/runtime/" + hashFrom(id) + "_anim_" + (int)(scale * 100));
        AnimatedDynamicTexture tex = new AnimatedDynamicTexture(nis, delays, true);
        Minecraft.getInstance().getTextureManager().register(rl, tex); // void
        return rl;
    }

    private static ResourceLocation key(ResourceLocation id, String suffix) {
        // gallery:img/<hash> -> gallery:textures/runtime/<hash>_<suffix>
        String hash = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
        String path = "textures/runtime/" + hash + "_" + suffix;
        return Gallery.locate(path);
    }

    /* ================= 工具：缩放/转 NativeImage ================= */

    private static String hashFrom(ResourceLocation id) {
        String p = id.getPath();
        int last = p.lastIndexOf('/');
        return last >= 0 ? p.substring(last + 1) : p;
    }

    private static BufferedImage scale(BufferedImage src, float scale) {
        int w = Math.max(1, Math.round(src.getWidth()  * scale));
        int h = Math.max(1, Math.round(src.getHeight() * scale));
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private static BufferedImage scaleToThumb(BufferedImage src) {
        // 最长边缩到 128px（你可改阈值）
        int max = Math.max(src.getWidth(), src.getHeight());
        float s = max <= 128 ? 1.0f : 128f / max;
        return scale(src, s);
    }

    private static float thumbScale(int w, int h) {
        int max = Math.max(w, h);
        return max <= 128 ? 1.0f : 128f / max;
    }

    private static NativeImage toNative(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        NativeImage ni = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8)  & 0xFF;
                int b = (argb)        & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                ni.setPixelRGBA(x, y, abgr);
            }
        }
        return ni;
    }

    private ClientPaintingImages() {}

}
