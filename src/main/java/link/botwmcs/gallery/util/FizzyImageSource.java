package link.botwmcs.gallery.util;

import link.botwmcs.fizzy.util.EasyImagesClient;
import link.botwmcs.gallery.Gallery;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FizzyImageSource implements ClientPaintingImages.ImageSource, AutoCloseable {
    private final EasyImagesClient client;
    private final Path cacheDir;
    private final String baseUrl;
    private static final Pattern EAZY_URL_WITH_HASH = Pattern.compile("/i/\\d{4}/\\d{2}/\\d{2}/([0-9a-fA-F]{64})-0(?:_\\d+)?\\.(png|jpe?g|gif|webp)$");


    public FizzyImageSource(EasyImagesClient client) {
        this.client = client;
        this.baseUrl = link.botwmcs.fizzy.Config.GALLERY_URL.get();
        this.cacheDir = Minecraft.getInstance().gameDirectory.toPath().resolve(link.botwmcs.fizzy.Config.IMAGE_LOC.get()).resolve(".cache");
    }
    public FizzyImageSource(EasyImagesClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.cacheDir = Minecraft.getInstance().gameDirectory.toPath().resolve(link.botwmcs.fizzy.Config.IMAGE_LOC.get()).resolve(".cache");
    }

    @Override
    public void close() throws Exception {
        try { client.close(); } catch (Exception ignored) {}
    }

    @Override
    public String ext(ResourceLocation id) {
        try {
            String url = resolveUrl(id);
            return extFromUrl(url);
        } catch (Exception e) {
            return "png";
        }
    }

    @Override
    public byte[] load(ResourceLocation id) throws Exception {
        String url = resolveUrl(id);
        Path dest = cachePathFor(url, extFromUrl(url));
        if (Files.exists(dest)) return Files.readAllBytes(dest);

        Files.createDirectories(dest.getParent());

        int maxAttempts = 3;
        long delayMs = 200; // 200ms 起步
        IOException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                CompletableFuture<Void> fut = client.downloadToAsync(url, dest);
                fut.get(); // 后台线程阻塞即可
                return Files.readAllBytes(dest);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof IOException io) {
                    last = io;
                    // 只在 404 时退避重试
                    String msg = io.getMessage();
                    boolean is404 = msg != null && msg.contains("HTTP 404");
                    if (is404 && attempt < maxAttempts) {
                        Thread.sleep(delayMs);
                        delayMs *= 2; // 200 -> 400 -> 800
                        continue;
                    }
                }
                throw ex; // 非 404 或已达最大次数
            }
        }
        throw last != null ? last : new IOException("download failed");

    }

    /* ============ Tools ============ */
    public static String extFromFilename(String name) {
        int i = name.lastIndexOf('.');
        if (i > 0 && i < name.length() - 1) {
            String e = name.substring(i + 1).toLowerCase(Locale.ROOT);
            if (e.matches("[a-z0-9]{3,5}")) return e;
        }
        return "png";
    }

    public static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(link.botwmcs.fizzy.Config.IMAGE_LOC.get()).resolve(".cache");
    }

    public static String buildContentKey(byte[] data, String ext) {
        String hex = sha256Hex(data);
        String safeExt = (ext == null || ext.isBlank()) ? "png" : ext.toLowerCase(Locale.ROOT);
        return hex + "." + safeExt;
    }


    /* ============ URL 解析 ============ */
    private String resolveUrl(ResourceLocation id) {
        if (!id.getNamespace().equals(Gallery.MODID)) {
            throw new IllegalArgumentException("Unexpected namespace: " + id);
        }
        String path = id.getPath();
        Gallery.LOGGER.info(path);

        if (path.startsWith("img/")) {
            // 这里的 key 允许包含子目录（yyyy/m/d/...），我们已经保证只含 [a-z0-9/._-]
            String key = path.substring("img/".length());
//            String withDash = appendDashZero(key);
            return this.baseUrl + "/i/" + key;
//        } else if (path.startsWith("url/")) {
//            String hex = path.substring("url/".length());
//            byte[] b = fromHex(hex);
//            return new String(b, StandardCharsets.UTF_8);
        } else {
            // 兜底：当成 /i/<path>
            return this.baseUrl + "/i/" + path;
        }
    }


    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(Character.forDigit((b >>> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String extFromUrl(String url) {
        int q = url.indexOf('?');
        String pure = q >= 0 ? url.substring(0, q) : url;
        int dot = pure.lastIndexOf('.');
        if (dot > pure.lastIndexOf('/')) {
            String ext = pure.substring(dot + 1).toLowerCase();
            if (ext.length() >= 3 && ext.length() <= 5) return ext;
        }
        return "png";
    }

    private Path cachePathFor(String url, String ext) throws Exception {
        Matcher m = EAZY_URL_WITH_HASH.matcher(url);
        if (m.find()) {
            String sha256 = m.group(1).toLowerCase(Locale.ROOT);
            String realExt = m.group(2).toLowerCase(Locale.ROOT);
            return this.cacheDir.resolve(sha256 + "." + realExt); // ← 关键：统一
        }
        // 非本图床 URL 才退回 URL-SHA1
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        String hex = toHex(md.digest(url.getBytes(StandardCharsets.UTF_8)));
        return this.cacheDir.resolve(hex + "." + (ext == null ? "png" : ext));


    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >>> 4) & 0xF, 16))
                .append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
