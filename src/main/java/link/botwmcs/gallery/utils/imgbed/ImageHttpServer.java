package link.botwmcs.gallery.utils.imgbed;

import com.sun.net.httpserver.HttpExchange;
import link.botwmcs.gallery.ClientConfig;
import link.botwmcs.gallery.ServerConfig;
import link.botwmcs.gallery.identify.Painting;
import link.botwmcs.gallery.network.PaintingSource;
import link.botwmcs.gallery.network.ServerNetHandlers;
import link.botwmcs.gallery.network.ServerPaintingManager;
import link.botwmcs.gallery.utils.imgbed.ticket.UploadTicket;
import net.minecraft.server.MinecraftServer;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageHttpServer {
    private final HttpServer http;
    private final ExecutorService exec;
    private final Path originalDir;
    private final Path derivedDir;
    private final String baseLocation;
    private final ServerPaintingManager paintingMgr;

    private final MinecraftServer mc;


    public ImageHttpServer(MinecraftServer server, ServerPaintingManager mgr, String bind, int port) throws IOException {
        this.paintingMgr = mgr;
        this.baseLocation = normalizeBase(ServerConfig.HTTP_SERVER_ADDR.get()) + ":" + ServerConfig.HTTP_SERVER_PORT.get();
        this.http = HttpServer.create(new InetSocketAddress(bind, port), 0);
        this.mc = server;
        this.exec = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Gallery-HTTP-Worker");
            t.setDaemon(true);
            return t;

        });
        Path base = server.getServerDirectory().resolve("images/imgbed");
        this.originalDir = base.resolve("original");
        this.derivedDir  = base.resolve("derived");
        Files.createDirectories(originalDir);
        Files.createDirectories(derivedDir);

        // 原图：/i/{sha}.{ext}
        http.createContext("/i", this::handleImage);
        // 缩略图/派生：/t/{sha}.png （可选）
        http.createContext("/t", this::handleThumb);

        http.setExecutor(exec);

    }

    public void start() {
        http.start();
    }
    public void stop() {
        http.stop(0);
        exec.shutdown();
    }

    private void handleUpload(HttpExchange ex) throws IOException {
        if (!("PUT".equalsIgnoreCase(ex.getRequestMethod()) || "POST".equalsIgnoreCase(ex.getRequestMethod()))) {
            send(ex, 405, ""); return;
        }
        String path = ex.getRequestURI().getPath(); // /upload/{ticketId}
        if (!path.startsWith("/upload/") || path.length() <= "/upload/".length()) {
            send(ex, 400, "bad path"); return;
        }
        String ticketId = path.substring("/upload/".length());

        // 1) 取出&消费票据（一次性）
        Optional<UploadTicket> opt = ServerNetHandlers.tickets().consume(ticketId);
        if (opt.isEmpty()) { send(ex, 403, "invalid or expired ticket"); return; }
        UploadTicket t = opt.get();

        // 2) 将请求体写入临时文件，同时做大小限制与 sha256 计算
        Path dst = originalDir.resolve(t.sha256LowerHex() + "." + t.ext());
        Path tmp = dst.resolveSibling(dst.getFileName().toString() + ".part");
        try {
            long max = Math.min(t.maxBytes(), ServerConfig.MAX_BYTE.getAsLong());
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            long written = 0L;
            try (var in = ex.getRequestBody();
                 var out = new DigestOutputStream(Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), md)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    written += n;
                    if (written > max) throw new IOException("too large");
                    out.write(buf, 0, n);
                }
            }
            String got = hex(md.digest());
            if (!got.equalsIgnoreCase(t.sha256LowerHex())) {
                Files.deleteIfExists(tmp);
                send(ex, 422, "sha256 mismatch"); return;
            }
            Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            send(ex, 500, "upload failed"); return;
        }

        // 3) 入库 & 广播元数据（MC 主线程执行更安全）
        mc.execute(() -> {
            Painting meta = t.meta();
            var src = new PaintingSource("url", baseLocation + "/i/" + t.sha256LowerHex() + "." + t.ext(),
                    t.sha256LowerHex(), t.ext(), meta.width(), meta.height());

            var motive = paintingMgr.registerPainting(meta, src);
            // TODO: 这里发送你已有的 S2CPaintingMeta
            // Network.sendToAll(new S2CPaintingMeta(motive, meta, src));
        });

        send(ex, 200, "{\"ok\":true}");
    }


    private void handleImage(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, ""); return; }
        // 路径格式：/i/abc123...8f.png
        String p = ex.getRequestURI().getPath(); // /i/xxx
        String file = p.substring("/i/".length()).toLowerCase(Locale.ROOT);
        if (!file.matches("[a-f0-9]{64}\\.(png|jpg|jpeg|gif|webp)")) { send(ex, 400, "bad path"); return; }

        Path target = originalDir.resolve(file);
        if (!Files.exists(target)) { send(ex, 404, "not found"); return; }

        // 缓存头
        String etag = file.substring(0, 64);
        ex.getResponseHeaders().set("ETag", "\"" + etag + "\"");
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
        ex.getResponseHeaders().set("Last-Modified", Instant.ofEpochMilli(Files.getLastModifiedTime(target).toMillis()).toString());
        ex.getResponseHeaders().set("Content-Type", guessContentType(file));

        byte[] bytes = Files.readAllBytes(target);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private void handleThumb(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, ""); return; }
        // 路径格式：/t/abc123...8f.png
        String file = ex.getRequestURI().getPath().substring("/t/".length()).toLowerCase(Locale.ROOT);
        if (!file.matches("[a-f0-9]{64}\\.png")) { send(ex, 400, "bad path"); return; }

        Path target = derivedDir.resolve(file);
        if (!Files.exists(target)) { send(ex, 404, "not found"); return; }

        ex.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
        ex.getResponseHeaders().set("Content-Type", "image/png");
        byte[] bytes = Files.readAllBytes(target);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }

    private static String guessContentType(String file) {
        if (file.endsWith(".png"))  return "image/png";
        if (file.endsWith(".jpg") || file.endsWith(".jpeg")) return "image/jpeg";
        if (file.endsWith(".gif"))  return "image/gif";
        if (file.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length*2);
        for (byte x : b) sb.append(Character.forDigit((x>>>4)&0xF,16)).append(Character.forDigit(x&0xF,16));
        return sb.toString();
    }

    private static void send(HttpExchange ex, int code, String msg) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        byte[] b = msg.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static String normalizeBase(String base) {
        if (base == null || base.isEmpty()) return "";
        return base.endsWith("/") ? base.substring(0, base.length()-1) : base;
    }




}
