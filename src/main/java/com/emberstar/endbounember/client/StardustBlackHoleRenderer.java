package com.emberstar.endbounember.client;

import com.emberstar.endbounember.entity.StardustBlackHoleEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** 星尘光环引力黑洞的球形客户端渲染器。 */
public class StardustBlackHoleRenderer extends EntityRenderer<StardustBlackHoleEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final float RADIUS = 1.0F;
    private static final int LATITUDES = 16;
    private static final int LONGITUDES = 24;

    public StardustBlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(StardustBlackHoleEntity entity, float entityYaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight) {
        float progress = entity.lifeProgress(partialTick);
        float appear = Math.min(1.0F, (entity.getAgeTicks() + partialTick) / 4.0F);
        float fade = progress > 0.92F ? Math.max(0.0F, (1.0F - progress) / 0.08F) : 1.0F;
        float radius = RADIUS * appear * fade;
        if (radius <= 0.001F) return;

        pose.pushPose();
        pose.translate(0.0D, 1.0D, 0.0D);
        drawPortalSphere(pose, buffers.getBuffer(RenderType.entityTranslucent(TEXTURE)), radius, packedLight);

        float time = (entity.getAgeTicks() + partialTick) * 0.035F;
        drawLensRings(pose, buffers, radius, fade, time, packedLight);
        pose.popPose();
    }

    private static void drawPortalSphere(PoseStack pose, VertexConsumer consumer, float radius, int packedLight) {
        PoseStack.Pose matrix = pose.last();
        for (int latitude = 0; latitude < LATITUDES; latitude++) {
            float v0 = latitude / (float) LATITUDES;
            float v1 = (latitude + 1) / (float) LATITUDES;
            float phi0 = (v0 - 0.5F) * (float) Math.PI;
            float phi1 = (v1 - 0.5F) * (float) Math.PI;
            float ring0 = (float) Math.cos(phi0);
            float ring1 = (float) Math.cos(phi1);
            float y0 = (float) Math.sin(phi0);
            float y1 = (float) Math.sin(phi1);
            for (int longitude = 0; longitude < LONGITUDES; longitude++) {
                float u0 = longitude / (float) LONGITUDES;
                float u1 = (longitude + 1) / (float) LONGITUDES;
                float theta0 = u0 * (float) (Math.PI * 2.0);
                float theta1 = u1 * (float) (Math.PI * 2.0);
                addPortalVertex(consumer, matrix, radius, ring0, y0, theta0, u0, v0);
                addPortalVertex(consumer, matrix, radius, ring1, y1, theta0, u0, v1);
                addPortalVertex(consumer, matrix, radius, ring1, y1, theta1, u1, v1);
                addPortalVertex(consumer, matrix, radius, ring0, y0, theta1, u1, v0);
            }
        }
    }

    private static void addPortalVertex(VertexConsumer consumer, PoseStack.Pose pose, float radius,
            float ring, float y, float theta, float u, float v) {
        // 球体使用原版纯白纹理，需用近黑色顶点色压暗，呈现实心黑球；带极轻的紫色调避免死黑
        consumer.addVertex(pose, radius * ring * (float) Math.cos(theta), radius * y,
                        radius * ring * (float) Math.sin(theta))
                .setColor(0.02F, 0.0F, 0.05F, 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, ring * (float) Math.cos(theta), y, ring * (float) Math.sin(theta));
    }

    private static void drawLensRings(PoseStack pose, MultiBufferSource buffers, float radius,
            float fade, float time, int packedLight) {
        VertexConsumer ring = buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotation(time));
        drawRing(pose.last(), ring, radius * 1.16F, radius * 1.48F, 0.16F * fade,
                0.45F, 0.75F, 1.0F, packedLight);
        pose.popPose();

        pose.pushPose();
        pose.mulPose(Axis.XP.rotation(0.72F));
        pose.mulPose(Axis.YP.rotation(-time * 0.65F));
        drawRing(pose.last(), ring, radius * 1.02F, radius * 1.23F, 0.20F * fade,
                0.34F, 0.68F, 1.0F, packedLight);
        pose.popPose();
    }

    private static void drawRing(PoseStack.Pose pose, VertexConsumer consumer, float innerRadius,
            float outerRadius, float alpha, float red, float green, float blue, int packedLight) {
        int segments = 64;
        for (int i = 0; i < segments; i++) {
            float a0 = i * (float) (Math.PI * 2.0 / segments);
            float a1 = (i + 1) * (float) (Math.PI * 2.0 / segments);
            addRingVertex(consumer, pose, innerRadius, a0, red, green, blue, alpha, packedLight);
            addRingVertex(consumer, pose, outerRadius, a0, red, green, blue, alpha * 0.18F, packedLight);
            addRingVertex(consumer, pose, outerRadius, a1, red, green, blue, alpha * 0.18F, packedLight);
            addRingVertex(consumer, pose, innerRadius, a1, red, green, blue, alpha, packedLight);
        }
    }

    private static void addRingVertex(VertexConsumer consumer, PoseStack.Pose pose, float radius,
            float angle, float red, float green, float blue, float alpha, int packedLight) {
        float x = (float) Math.cos(angle) * radius;
        float z = (float) Math.sin(angle) * radius;
        consumer.addVertex(pose, x, 0.0F, z)
                .setColor(red, green, blue, alpha)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(StardustBlackHoleEntity entity) {
        return TEXTURE;
    }
}
