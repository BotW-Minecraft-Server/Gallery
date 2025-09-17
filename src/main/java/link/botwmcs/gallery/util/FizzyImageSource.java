package link.botwmcs.gallery.util;

import link.botwmcs.fizzy.util.EasyImagesClient;
import link.botwmcs.gallery.Gallery;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public final class FizzyImageSource implements ClientPaintingImages.ImageSource, AutoCloseable {
    private final EasyImagesClient client;
    private final Path cacheDir;
    private final String baseUrl;

    public FizzyImageSource(EasyImagesClient client) {
        this.client = client;
        this.baseUrl = link.botwmcs.fizzy.Config.GALLERY_URL.get();
        this.cacheDir = Minecraft.getInstance().gameDirectory.toPath().resolve(link.botwmcs.fizzy.Config.IMAGE_LOC.get());
    }
    public FizzyImageSource(EasyImagesClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.cacheDir = Minecraft.getInstance().gameDirectory.toPath().resolve(link.botwmcs.fizzy.Config.IMAGE_LOC.get());
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
        // 命中缓存
        if (Files.exists(dest)) {
            return Files.readAllBytes(dest);
        }
        // 下载（在我们自己的线程中调用，OK）
        Files.createDirectories(dest.getParent());
        CompletableFuture<Void> fut = client.downloadToAsync(url, dest);
        fut.get(); // 阻塞当前后台线程，非主线程
        return Files.readAllBytes(dest);
    }


    /* ============ URL 解析 ============ */
    private String resolveUrl(ResourceLocation id) {
        if (!id.getNamespace().equals(Gallery.MODID)) {
            throw new IllegalArgumentException("Unexpected namespace: " + id);
        }
        String path = id.getPath();
        if (path.startsWith("img/")) {
            String key = path.substring("img/".length());
            // 你可以把这行换成 baseUrl + "/img/" + key + ".png" 等你的服务器规则
            return this.baseUrl + "/i/" + urlEncode(key);
        } else if (path.startsWith("url/")) {
            String b64 = path.substring("url/".length());
            return new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8);
        } else {
            // 兜底：当成 /i/<path>
            return this.baseUrl + "/i/" + urlEncode(path);
        }
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
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

    private static Path cachePathFor(String url, String ext) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest(url.getBytes(StandardCharsets.UTF_8));
        String hex = toHex(digest);
        return Minecraft.getInstance().gameDirectory.toPath().resolve(link.botwmcs.fizzy.Config.IMAGE_LOC.get()).resolve(hex + "." + ext);
    }

    private static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >>> 4) & 0xF, 16))
                .append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }
}
