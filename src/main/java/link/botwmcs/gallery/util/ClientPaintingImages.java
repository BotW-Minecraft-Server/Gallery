package link.botwmcs.gallery.util;

import com.mojang.blaze3d.platform.NativeImage;
import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.entity.Painting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public final class ClientPaintingImages {
    public interface ImageSource {
        /** 基于 id 返回图片字节；可从磁盘缓存或网络抓取。异常由上层吞并并回退占位。 */
        byte[] load(ResourceLocation id) throws Exception;
        /** 可选：返回扩展名用于命名（png/gif/webp）；可返回 null。 */
        default String ext(ResourceLocation id) { return "png"; }
    }

    private static <T> CompletableFuture<T> onMcThread(java.util.function.Supplier<T> task) {
        CompletableFuture<T> f = new CompletableFuture<>();
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            try { f.complete(task.get()); } catch (Throwable t) { f.completeExceptionally(t); }
        });
        return f;
    }
    private static CompletableFuture<Void> onMcThread(Runnable run) {
        return onMcThread(() -> { run.run(); return null; });
    }

    public static final ConcurrentMap<ResourceLocation, EnumMap<Painting.Size, ResourceLocation>> TEXTURES = new ConcurrentHashMap<>();
    public static final ConcurrentMap<ResourceLocation, ImageMeta> METAS = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Set<ResourceLocation> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    /** 界面用：本地文件 → 缩略图纹理缓存 */
    public record Thumb(ResourceLocation rl, int width, int height) {}

    public static final ConcurrentMap<Path, Thumb> THUMBS = new ConcurrentHashMap<>();
    public static final ConcurrentMap<Path, Thumb> FULLS = new ConcurrentHashMap<>();

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
        if (id == null || "none".equals(id.getPath()) || source == null) return;
        if (!IN_FLIGHT.add(id)) return; // 已在加载中

        POOL.submit(() -> {
            try {
                byte[] bytes = source.load(id);
                if (bytes == null || bytes.length == 0) return;

                Decoded decoded = decode(bytes);
                METAS.put(id, new ImageMeta(decoded.width, decoded.height, decoded.animated));
                onMcThread(() -> {
                    EnumMap<Painting.Size, ResourceLocation> out = new EnumMap<>(Painting.Size.class);
                    registerAllSizes(id, decoded, out);
                    TEXTURES.put(id, out);
                });
            } catch (Throwable t) {
                // 失败时保持使用占位纹理
                Gallery.LOGGER.warn("[Gallery] ensureLocalTextures failed: {}", id, t);
            } finally {
                IN_FLIGHT.remove(id);
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
    /* ================= 工具：缩略图与大图 ================= */

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

    /** 界面用：取已缓存的缩略图；无则 null。 */
    public static @Nullable Thumb getCachedThumb(Path file) {
        return THUMBS.get(file);
    }

    public static @Nullable Thumb getCachedFull(Path file) {
        return FULLS.get(file);
    }

    /** 异步生成/注册缩略图；已有则直接返回。调用方可 thenAccept 在 UI 刷新。 */
    public static CompletableFuture<Thumb> ensureThumb(Path file, int targetEdge, boolean animateGif) {
        Thumb cached = THUMBS.get(file);
        // 如果已有并且尺寸够且（请求动图时）已有 anim 版本，就直接用
        if (cached != null && Math.max(cached.width(), cached.height()) >= targetEdge && (!animateGif || cached.rl().getPath().contains("_anim_"))) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
                    try {
                        byte[] bytes = java.nio.file.Files.readAllBytes(file);
                        boolean isGif = isGif(bytes);

                        if (animateGif && isGif) {
                            // GIF：解码帧并缩放到目标边
                            GifDecoder.Result r = GifDecoder.decode(bytes);
                            java.util.List<java.awt.image.BufferedImage> scaled = new java.util.ArrayList<>(r.frames.size());
//                            for (var f : r.frames) scaled.add(scaleToFit(f, targetEdge));
                            float s = preferHalfThenFit(r.frames.get(0).getWidth(), r.frames.get(0).getHeight(), targetEdge);
                            for (var f : r.frames) scaled.add(scale(f, s));
                            return new Object[]{ "GIF", scaled, sanitizeDelays(r.delaysMs) };
                        } else {
                            // 静态图：ImageIO 读并缩放
                            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                            if (img == null) throw new IllegalStateException("Unsupported image: " + file);
                            float s = preferHalfThenFit(img.getWidth(), img.getHeight(), targetEdge);
                            BufferedImage thumb = scale(img, s);
//                            java.awt.image.BufferedImage thumb = scaleToFit(img, targetEdge);
                            return new Object[]{ "PNG", thumb };
                        }
                    } catch (Throwable e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                }, POOL)

                // ② 渲染线程：构造 DynamicTexture / AnimatedDynamicTexture 并注册
                .thenCompose(obj -> onMcThread(() -> {
                    try {
                        String kind = (String) obj[0];
                        ResourceLocation rl;
                        Thumb t;

                        if ("GIF".equals(kind)) {
                            @SuppressWarnings("unchecked")
                            List<BufferedImage> frames = (List<BufferedImage>) obj[1];
                            int[] delays = (int[]) obj[2];

                            // 转 NativeImage[]
                            NativeImage[] nis = new NativeImage[frames.size()];
                            for (int i = 0; i < frames.size(); i++) nis[i] = toNative(frames.get(i));

                            rl = Gallery.locate("textures/runtime/thumb/" + hashThumbKey(file) + "_anim_" + targetEdge);
                            AnimatedDynamicTexture tex = new AnimatedDynamicTexture(nis, delays, true);
                            tex.setFilter(false, false);
                            Minecraft.getInstance().getTextureManager().register(rl, tex);
                            t = new Thumb(rl, frames.get(0).getWidth(), frames.get(0).getHeight());
                        } else {
                            BufferedImage thumb = (BufferedImage) obj[1];
                            NativeImage ni = toNative(thumb);
                            rl = Gallery.locate("textures/runtime/thumb/" + hashThumbKey(file) + "_" + targetEdge);
                            DynamicTexture dyn = new DynamicTexture(ni);
                            dyn.setFilter(false, false); // 这句必须在渲染线程
                            Minecraft.getInstance().getTextureManager().register(rl, dyn);
                            t = new Thumb(rl, thumb.getWidth(), thumb.getHeight());
                        }

                        THUMBS.put(file, t);
                        return t;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }))

                // ③ 异常日志
                .exceptionally(ex -> {
                    Gallery.LOGGER.warn("[Gallery] ensureThumb failed: {}", file, ex);
                    return null;
                });
    }

    public static CompletableFuture<Thumb> ensureFull(Path file, boolean animateGif) {
        Thumb cached = FULLS.get(file);
        if (cached != null && (!animateGif || cached.rl().getPath().contains("_anim_"))) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        byte[] bytes = java.nio.file.Files.readAllBytes(file);

                        if (animateGif && isGif(bytes)) {
                            GifDecoder.Result r = GifDecoder.decode(bytes);
                            // 不缩放，保留原分辨率
                            java.util.List<java.awt.image.BufferedImage> frames = r.frames;
                            int[] delays = sanitizeDelays(r.delaysMs);

                            // 先在后台把像素转换好（NativeImage 构造仅 CPU）
                            com.mojang.blaze3d.platform.NativeImage[] nis = new com.mojang.blaze3d.platform.NativeImage[frames.size()];
                            for (int i = 0; i < frames.size(); i++) {
                                nis[i] = toNative(frames.get(i));
                            }
                            return new Object[]{"GIF", nis, delays};
                        } else {
                            java.awt.image.BufferedImage img =
                                    javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                            if (img == null) throw new IllegalStateException("Unsupported image: " + file);

                            com.mojang.blaze3d.platform.NativeImage ni = toNative(img);
                            return new Object[]{"PNG", ni};
                        }
                    } catch (Throwable e) {
                        throw new java.util.concurrent.CompletionException(e);
                    }
                }, POOL)

                // ② 主线程：创建纹理对象、setFilter、register
                .thenCompose(obj -> onMcThread(() -> {
                    try {
                        String kind = (String) obj[0];
                        ResourceLocation rl;
                        Thumb t;

                        if ("GIF".equals(kind)) {
                            com.mojang.blaze3d.platform.NativeImage[] nis =
                                    (com.mojang.blaze3d.platform.NativeImage[]) obj[1];
                            int[] delays = (int[]) obj[2];

                            rl = Gallery.locate("textures/runtime/full/" + hashThumbKey(file) + "_anim_full");
                            AnimatedDynamicTexture tex = new AnimatedDynamicTexture(nis, delays, true);
                            tex.setFilter(false, false); // 禁用 blur/mipmap
                            Minecraft.getInstance().getTextureManager().register(rl, tex);

                            t = new Thumb(rl, nis[0].getWidth(), nis[0].getHeight());
                        } else {
                            com.mojang.blaze3d.platform.NativeImage ni =
                                    (com.mojang.blaze3d.platform.NativeImage) obj[1];

                            rl = Gallery.locate("textures/runtime/full/" + hashThumbKey(file) + "_full");
                            net.minecraft.client.renderer.texture.DynamicTexture dyn =
                                    new net.minecraft.client.renderer.texture.DynamicTexture(ni);
                            dyn.setFilter(false, false);
                            Minecraft.getInstance().getTextureManager().register(rl, dyn);

                            t = new Thumb(rl, ni.getWidth(), ni.getHeight());
                        }

                        FULLS.put(file, t);
                        return t;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }))

                // ③ 日志
                .exceptionally(ex -> {
                    Gallery.LOGGER.warn("[Gallery] ensureFull failed: {}", file, ex);
                    return null;
                });

    }


    private static int[] sanitizeDelays(int[] in) {
        if (in == null || in.length == 0) return new int[]{100};
        int[] out = new int[in.length];
        for (int i = 0; i < in.length; i++) out[i] = Math.max(20, in[i]);
        return out;
    }


    // 工具：把图片按给定最大边缩放（保持等比）
    private static BufferedImage scaleToFit(BufferedImage src, int maxEdge) {
        int max = Math.max(src.getWidth(), src.getHeight());
        float s = max <= maxEdge ? 1.0f : (float) maxEdge / max;
        return scale(src, s); // 复用你已有的 scale(...)
    }

    private static float preferHalfThenFit(int srcW, int srcH, int maxEdge) {
        float half = 0.5f;
        float halfEdge = Math.max(srcW, srcH) * half;
        if (halfEdge <= maxEdge) return half;                // 能用 1/2 就用 1/2，最清晰
        return (float) maxEdge / Math.max(srcW, srcH);       // 否则再缩到不超过上限
    }

    /** 缩略图 key：路径 + mtime + size，避免同名文件更新后拿到旧图 */
    private static String hashThumbKey(Path file) throws Exception {
        long mtime = java.nio.file.Files.getLastModifiedTime(file).toMillis();
        long size  = java.nio.file.Files.size(file);
        var md = java.security.MessageDigest.getInstance("SHA-1");
        md.update(file.toAbsolutePath().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        md.update(java.nio.ByteBuffer.allocate(16).putLong(mtime).putLong(size).array());
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(Character.forDigit((b >>> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        return sb.toString();
    }


    private ClientPaintingImages() {}

}
