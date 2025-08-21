package link.botwmcs.gallery.utils.imgbed.ticket;

import link.botwmcs.gallery.identify.Painting;

import java.util.UUID;

public record UploadTicket(
        String id,
        UUID uploader,
        String sha256LowerHex,
        String ext,
        long   maxBytes,
        long   expiresAtMs,
        Painting meta // 省事直接放 Painting
) {
    public boolean expiredNow() { return System.currentTimeMillis() > expiresAtMs; }
}