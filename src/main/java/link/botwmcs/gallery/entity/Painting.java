package link.botwmcs.gallery.entity;

import link.botwmcs.gallery.Gallery;
import net.minecraft.resources.ResourceLocation;

public interface Painting {
    /** 占位贴图（请确保资源包内有该纹理，或改成你的路径） */
    ResourceLocation DEFAULT_IDENTIFIER = Gallery.locate("textures/block/frame/canvas.png");

    /** 贴图分级（与 ClientPaintingImages 的注册/选择保持一致） */
    enum Size {
        FULL,
        HALF,
        QUARTER,
        EIGHTH,
        THUMBNAIL
    }
}
