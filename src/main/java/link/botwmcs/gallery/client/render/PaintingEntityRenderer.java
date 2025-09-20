package link.botwmcs.gallery.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import link.botwmcs.gallery.Gallery;
import link.botwmcs.gallery.Services;
import link.botwmcs.gallery.entity.Painting;
import link.botwmcs.gallery.entity.PaintingEntity;
import link.botwmcs.gallery.util.ClientPaintingImages;
import link.botwmcs.gallery.util.BlenderObjectLoader;
import link.botwmcs.gallery.util.oobj.Face;
import link.botwmcs.gallery.util.oobj.FaceVertex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class PaintingEntityRenderer<T extends PaintingEntity> extends EntityRenderer<T> {
    public PaintingEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }
    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Custom rendering code for the painting entity
        // You can use the entity's data to determine what to render
        // For example, you might want to render an image based on a URL stored in the entity
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-entity.getViewXRot(partialTicks)));
        poseStack.scale(0.0625F, 0.0625F, 0.0625F);

        this.renderPainting(poseStack, buffer, entity);
        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(T t) {
        Minecraft minecraft = Minecraft.getInstance();
        double distance = (double) (minecraft.player == null ? 0.0F : minecraft.player.distanceTo(t));
        double blocksVisible = Math.tan((double)(Integer) minecraft.options.fov().get() / (double)180.0F * Math.PI / (double)2.0F) * (double)2.0F * distance;

        ClientPaintingImages.ImageMeta meta = ClientPaintingImages.METAS.get(t.getPaint());
        double resolution = 1.0;
        if (meta != null) {
            double pxPerBlockW = (double) meta.pixelW / Math.max(1, t.getPaintingWidth());
            double pxPerBlockH = (double) meta.pixelH / Math.max(1, t.getPaintingHeight());
            resolution = Math.max(pxPerBlockW, pxPerBlockH) / 16.0; // 每格 16px 基准
        }
        double pixelDensity = blocksVisible * resolution / minecraft.getWindow().getHeight();
        Painting.Size size = pixelDensity > 16.0F
                ? Painting.Size.THUMBNAIL : pixelDensity > 16.0F
                ? Painting.Size.EIGHTH : pixelDensity > 16.0F
                ? Painting.Size.QUARTER : pixelDensity > 16.0F
                ? Painting.Size.HALF : Painting.Size.FULL;

        return ClientPaintingImages.getImageIdentifier(t.getPaint(), size, Services.IMAGE_SOURCE);
    }

    private int getLight(int light, boolean glowing) {
        return !glowing ? light : LightTexture.pack((int)((double)LightTexture.block(light) * (double)0.25F + (double)11.25F), LightTexture.sky(light));
    }

    private int getFrameLight(int light, boolean glowing) {
        return !glowing ? light : LightTexture.pack((int)((double)LightTexture.block(light) * (double)0.875F + (double)2.0F), LightTexture.sky(light));
    }

    private void renderPainting(PoseStack stack, MultiBufferSource bufferSource, T entity) {
        int light = LevelRenderer.getLightColor(entity.level(), entity.blockPosition());
        PoseStack.Pose pose = stack.last();
        boolean hasFrame = !entity.getFrame().getPath().equals("none");
        int widthPixels  = entity.getPaintingWidth()  * 16;
        int heightPixels = entity.getPaintingHeight() * 16;
        boolean glowing  = entity.isGlowing();

        // 纹理（画）面的 buffer
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entitySolid(this.getTextureLocation(entity)));

        // —— 计算边距 —— //
        float frameInset = hasFrame ? 1.0f : 0.0f; // 保持原有那 1px 的“防越界”UV inset
        float geomMarginX = frameInset;
        float geomMarginY = frameInset;
        float uvInsetX    = frameInset;
        float uvInsetY    = frameInset;

        // TODO
        if (!entity.isAutoFit()) {
            // 保持原图宽高比（信箱/遮幅式）
            ClientPaintingImages.ImageMeta meta = ClientPaintingImages.METAS.get(entity.getPaint());
            if (meta != null && meta.pixelW > 0 && meta.pixelH > 0) {
                float rTex = meta.pixelW / (float) meta.pixelH;
                float rEnt = widthPixels / (float) heightPixels;

                if (rTex > rEnt) {
                    // 图更宽 → 以宽为基准，留上下边
                    float contentH = widthPixels / rTex;
                    float extraY = Math.max(0f, (heightPixels - contentH) / 2f);
                    geomMarginY += extraY;
                } else if (rTex < rEnt) {
                    // 图更高 → 以高为基准，留左右边
                    float contentW = heightPixels * rTex;
                    float extraX = Math.max(0f, (widthPixels - contentW) / 2f);
                    geomMarginX += extraX;
                }
                // 注意：UV 只保留 frameInset 的细微 inset，不随几何边距一起缩放，避免“裁切”图像
            }
            // 若没有 meta，退化为拉伸（即只用 frameInset）
        }

        // —— 画布（图像）——
        this.renderFaces(
                "objects/canvas.obj",
                pose,
                vc,
                this.getLight(light, glowing),
                (float) widthPixels,
                (float) heightPixels,
                geomMarginX, geomMarginY,
                uvInsetX, uvInsetY
        );

        // —— 相框（尺寸不变）——
        if (hasFrame) {
            vc = bufferSource.getBuffer(RenderType.entityCutout(entity.getMaterial()));
            this.renderFrame(
                    entity.getFrame(),
                    pose,
                    vc,
                    this.getFrameLight(light, glowing),
                    (float) widthPixels,
                    (float) heightPixels
            );
        }
    }

    private void renderFaces(String name, PoseStack.Pose pose, VertexConsumer vc, int light, float width, float height, float margin) {
        renderFaces(name, pose, vc, light, width, height, margin, margin, margin, margin);
    }

    private void renderFaces(String name, PoseStack.Pose pose, VertexConsumer vc, int light, float width, float height, float geomMarginX, float geomMarginY, float uvInsetX, float uvInsetY) {
        float usedW = Math.max(1f, width  - geomMarginX * 2f);
        float usedH = Math.max(1f, height - geomMarginY * 2f);

        float uvInsetU = uvInsetX / width;   // 转成 0..1
        float uvInsetV = uvInsetY / height;

        float uvScaleU = 1f - uvInsetU * 2f; // 只为防越界的小 inset
        float uvScaleV = 1f - uvInsetV * 2f;

        for (Face f : BlenderObjectLoader.objects.get(Gallery.locate(name))) {
            for (FaceVertex v : f.vertices) {
                float px = v.v.x * usedW;
                float py = v.v.y * usedH;
                float pz = v.v.z * 16.0f;

                float u = v.t.u * uvScaleU + uvInsetU;
                float vtx = (1.0f - v.t.v) * uvScaleV + uvInsetV; // 原代码有 1-v

                this.vertex(pose, vc, px, py, pz, u, vtx, v.n.x, v.n.y, v.n.z, light);
            }
        }
    }


    private List<Face> getFaces(ResourceLocation frame, String part) {
        String var10000 = frame.getNamespace();
        String var10001 = frame.getPath();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(var10000, var10001 + "/" + part + ".obj");
        return BlenderObjectLoader.objects.containsKey(id) ? (List)BlenderObjectLoader.objects.get(id) : List.of();
    }

    private void renderFrame(ResourceLocation frame, PoseStack.Pose pose, VertexConsumer vertexConsumer, int light, float width, float height) {
        List<Face> faces = this.getFaces(frame, "bottom");

        for(int x = 0; (float)x < width / 16.0F; ++x) {
            float u = width == 16.0F ? 0.75F : (x == 0 ? 0.0F : ((float)x == width / 16.0F - 1.0F ? 0.5F : 0.25F));

            for(Face face : faces) {
                for(FaceVertex v : face.vertices) {
                    this.vertex(pose, vertexConsumer, v.v.x + (float)(x * 16) - (width - 16.0F) / 2.0F, v.v.y - (height - 16.0F) / 2.0F, v.v.z, v.t.u * 0.25F + u, 1.0F - v.t.v, v.n.x, v.n.y, v.n.z, light);
                }
            }
        }

        faces = this.getFaces(frame, "top");

        for(int x = 0; (float)x < width / 16.0F; ++x) {
            float u = width == 16.0F ? 0.75F : (x == 0 ? 0.0F : ((float)x == width / 16.0F - 1.0F ? 0.5F : 0.25F));

            for(Face face : faces) {
                for(FaceVertex v : face.vertices) {
                    this.vertex(pose, vertexConsumer, v.v.x + (float)(x * 16) - (width - 16.0F) / 2.0F, v.v.y + (height - 16.0F) / 2.0F, v.v.z, v.t.u * 0.25F + u, 1.0F - v.t.v, v.n.x, v.n.y, v.n.z, light);
                }
            }
        }

        faces = this.getFaces(frame, "right");

        for(int y = 0; (float)y < height / 16.0F; ++y) {
            float u = 0.25F;

            for(Face face : faces) {
                for(FaceVertex v : face.vertices) {
                    this.vertex(pose, vertexConsumer, v.v.x + (width - 16.0F) / 2.0F, v.v.y + (float)(y * 16) - (height - 16.0F) / 2.0F, v.v.z, v.t.u * 0.25F + u, 1.0F - v.t.v, v.n.x, v.n.y, v.n.z, light);
                }
            }
        }

        faces = this.getFaces(frame, "left");

        for(int y = 0; (float)y < height / 16.0F; ++y) {
            float u = 0.25F;

            for(Face face : faces) {
                for(FaceVertex v : face.vertices) {
                    this.vertex(pose, vertexConsumer, v.v.x - (width - 16.0F) / 2.0F, v.v.y + (float)(y * 16) - (height - 16.0F) / 2.0F, v.v.z, v.t.u * 0.25F + u, 1.0F - v.t.v, v.n.x, v.n.y, v.n.z, light);
                }
            }
        }

    }

    private void vertex(PoseStack.Pose pose, VertexConsumer vertexConsumer, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ, int light) {
        vertexConsumer.addVertex(pose, x, y, z - 0.5F).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, normalX, normalY, normalZ);
    }


}
