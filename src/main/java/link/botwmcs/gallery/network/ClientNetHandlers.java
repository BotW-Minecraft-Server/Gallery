package link.botwmcs.gallery.network;

import com.mojang.datafixers.types.Type;
import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.network.c2s.C2SCreateUploadTicketPayload;
import link.botwmcs.gallery.network.s2c.S2CUploadTicketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;

public class ClientNetHandlers {


    public static void onS2CUploadTicket(final S2CUploadTicketPayload msg, final IPayloadContext ctx) {
        // 这里取“刚选的本地文件”——按你的 GUI 逻辑存放
        // todo: pending gui
        Path file = Path.of("test.png");
        if (file == null) return;

        ctx.enqueueWork(() -> {
            try {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(msg.uploadUrl()))
                        .timeout(Duration.ofSeconds(120))
                        .PUT(HttpRequest.BodyPublishers.ofFile(file))
                        .build();
                HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
                if (resp.statusCode() / 100 != 2) {
                    Gallery.LOGGER.warn("Upload failed: HTTP {}", resp.statusCode());
                } else {
                    Gallery.LOGGER.info("Upload OK: {}", file.getFileName());
                }
            } catch (Exception e) {
                Gallery.LOGGER.warn("Upload exception", e);
            }
        });
    }

}
