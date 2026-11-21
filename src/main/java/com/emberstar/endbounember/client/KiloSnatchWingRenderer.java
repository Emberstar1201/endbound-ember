package com.emberstar.endbounember.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.ClientHooks;

import java.util.ArrayList;
import java.util.List;

/** 第三人称呼吸式法阵，移植自 dsh 的千位劫取（kilosnatch，MIT）。 */
public final class KiloSnatchWingRenderer {
    private static final int PERIOD_TICKS = 50;
    private static final float EXHALE = 0.6F;
    private static final float MAX_SHIFT = 0.16F;
    private static final float TRAIL_AXIS = 1.3258F;
    private static final float DIAGONAL = 0.7071F;
    private static final float FRONT_Z = 0.538F;
    private static final float BACK_Z = 0.462F;
    private static final float GEM_CENTER = 0.327F;
    private static final float GEM_Y_MODEL = 7.4F;

    private KiloSnatchWingRenderer() {
    }

    public static boolean render(
            PoseStack poses, MultiBufferSource buffers, int light, int overlay,
            BakedModel model, ItemDisplayContext context, boolean leftHand) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel missingModel = minecraft.getModelManager().getMissingModel();
        if (model == null || model == missingModel) {
            return false;
        }

        poses.pushPose();
        try {
            BakedModel transformedModel = ClientHooks.handleCameraTransforms(poses, model, context, leftHand);
            if (transformedModel == null || transformedModel == missingModel || transformedModel.isCustomRenderer()) {
                return false;
            }
            List<BakedQuad> quads = collectQuads(transformedModel);
            if (quads.isEmpty()) {
                return false;
            }

            long tick = minecraft.level == null ? 0L : minecraft.level.getGameTime();
            float phase = (float) (tick % PERIOD_TICKS) / PERIOD_TICKS;
            boolean exhaling = phase < EXHALE;
            float expansion = exhaling ? phase / EXHALE : (1.0F - phase) / (1.0F - EXHALE);
            float easedExpansion = smooth(expansion);
            poses.translate(-0.5F, -0.5F, -0.5F);
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
            for (BakedQuad quad : quads) {
                renderModelQuad(poses, buffer, light, overlay, quad, MAX_SHIFT * easedExpansion);
            }

            float growth = exhaling ? easedExpansion : 1.0F;
            float contraction = exhaling ? 0.0F : (phase - EXHALE) / (1.0F - EXHALE);
            float radius = (0.06F + 0.16F * growth) * (1.0F - 0.55F * contraction);
            float rotation = contraction * (float) (Math.PI / 2);
            float brightness = exhaling ? growth : 1.0F - contraction;
            TextureAtlasSprite sprite = quads.getFirst().getSprite();
            for (int side = 1; side >= -1; side -= 2) {
                EffectFace face = new EffectFace(poses.last(), buffer, light, overlay, side);
                if (exhaling) {
                    renderTrail(face, sprite, expansion);
                }
                if (brightness > 0.02F && radius > 0.02F) {
                    renderCircle(face, sprite, radius, rotation, brightness);
                }
            }
            return true;
        } finally {
            poses.popPose();
        }
    }

    private static List<BakedQuad> collectQuads(BakedModel model) {
        RandomSource random = RandomSource.create(42L);
        List<BakedQuad> quads = model.getQuads(null, null, random);
        if (!quads.isEmpty()) {
            return quads;
        }
        List<BakedQuad> sidedQuads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            sidedQuads.addAll(model.getQuads(null, direction, random));
        }
        return sidedQuads;
    }

    private static void renderModelQuad(
            PoseStack poses, VertexConsumer buffer, int light, int overlay, BakedQuad quad, float shift) {
        int[] vertices = quad.getVertices();
        float x = 0.0F;
        float y = 0.0F;
        for (int vertex = 0; vertex < FaceBakery.VERTEX_COUNT; vertex++) {
            int offset = vertex * FaceBakery.VERTEX_INT_SIZE;
            x += Float.intBitsToFloat(vertices[offset]);
            y += Float.intBitsToFloat(vertices[offset + 1]);
        }
        x /= FaceBakery.VERTEX_COUNT;
        y /= FaceBakery.VERTEX_COUNT;
        double across = (x - y) * 11.3137;
        double along = (x + y) * 11.3137;
        if (Math.abs(across) > 1.3 && along > 4.0 && along < 10.0) {
            double fromGem = along - GEM_Y_MODEL;
            double length = Math.sqrt(across * across + fromGem * fromGem);
            double dx = (across + fromGem) * 0.7071 / length;
            double dy = (fromGem - across) * 0.7071 / length;
            poses.pushPose();
            try {
                poses.translate((float) (dx * shift), (float) (dy * shift), 0.0F);
                buffer.putBulkData(poses.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, light, overlay);
            } finally {
                poses.popPose();
            }
        } else {
            buffer.putBulkData(poses.last(), quad, 1.0F, 1.0F, 1.0F, 1.0F, light, overlay);
        }
    }

    private static void renderTrail(EffectFace face, TextureAtlasSprite sprite, float expansion) {
        float start = 0.72F - 0.47F * expansion;
        float minU = Mth.lerp(0.602F, sprite.getU0(), sprite.getU1());
        float maxU = Mth.lerp(0.638F, sprite.getU0(), sprite.getU1());
        for (int i = 0; i < 6; i++) {
            float progress = start + i * 0.03F;
            float center = TRAIL_AXIS * progress;
            float along = DIAGONAL * (0.022F - i * 0.0022F);
            float across = DIAGONAL * (0.014F - i * 0.0012F);
            float brightness = 1.0F - i * 0.13F;
            float minV = Mth.lerp(Math.max(0.0F, 1.0F - progress - 0.022F), sprite.getV0(), sprite.getV1());
            float maxV = Mth.lerp(Math.min(1.0F, 1.0F - progress + 0.022F), sprite.getV0(), sprite.getV1());

            EffectVertex a = new EffectVertex(center - along + across, center - along - across, minU, minV);
            EffectVertex b = new EffectVertex(center - along - across, center - along + across, minU, maxV);
            EffectVertex c = new EffectVertex(center + along - across, center + along + across, maxU, maxV);
            EffectVertex d = new EffectVertex(center + along + across, center + along - across, maxU, minV);
            if (face.side() > 0) {
                face.quad(brightness, a, b, c, d);
            } else {
                face.quad(brightness, a, d, c, b);
            }
        }
    }

    private static void renderCircle(
            EffectFace face, TextureAtlasSprite sprite, float radius, float rotation, float brightness) {
        float u = Mth.lerp(0.62F, sprite.getU0(), sprite.getU1());
        float v = Mth.lerp(0.72F, sprite.getV0(), sprite.getV1());
        float diamondU = Mth.lerp(0.539F, sprite.getU0(), sprite.getU1());
        float diamondV = Mth.lerp(0.75F, sprite.getV0(), sprite.getV1());
        float width = 0.011F;
        ring(face, radius, rotation, 36, width, u, v, brightness);
        ring(face, radius * 0.84F, -rotation * 1.3F, 40, width * 0.7F, u, v, brightness);
        ring(face, radius * 0.58F, rotation * 0.8F, 24, width * 0.6F, u, v, brightness);

        for (int i = 0; i < 24; i++) {
            double angle = rotation + i * (Math.PI / 12);
            ray(face, angle, radius, radius * 1.07F, width * 1.2F, u, v, brightness);
        }
        for (int i = 0; i < 8; i++) {
            double angle = rotation + i * (Math.PI / 4);
            ray(face, angle, radius * 0.55F, radius * 1.18F, width * 1.5F, u, v, brightness);
        }
        for (int i = 0; i < 8; i++) {
            double angle = rotation + i * (Math.PI / 4);
            float x = GEM_CENTER + (float) Math.cos(angle) * radius * 0.84F;
            float y = GEM_CENTER + (float) Math.sin(angle) * radius * 0.84F;
            float size = 0.012F * (0.6F + brightness);
            float stroke = width * 0.8F;
            face.line(x, y + size, x + size, y, stroke, diamondU, diamondV, brightness);
            face.line(x + size, y, x, y - size, stroke, diamondU, diamondV, brightness);
            face.line(x, y - size, x - size, y, stroke, diamondU, diamondV, brightness);
            face.line(x - size, y, x, y + size, stroke, diamondU, diamondV, brightness);
        }
    }

    private static void ring(
            EffectFace face, float radius, float rotation, int segments, float width, float u, float v, float brightness) {
        double step = Math.PI * 2 / segments;
        for (int i = 0; i < segments; i++) {
            double start = rotation + i * step;
            double end = rotation + (i + 1) * step;
            face.line(
                    GEM_CENTER + (float) Math.cos(start) * radius, GEM_CENTER + (float) Math.sin(start) * radius,
                    GEM_CENTER + (float) Math.cos(end) * radius, GEM_CENTER + (float) Math.sin(end) * radius,
                    width, u, v, brightness);
        }
    }

    private static void ray(
            EffectFace face, double angle, float inner, float outer, float width, float u, float v, float brightness) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        face.line(GEM_CENTER + cos * inner, GEM_CENTER + sin * inner,
                GEM_CENTER + cos * outer, GEM_CENTER + sin * outer, width, u, v, brightness);
    }

    private static float smooth(float progress) {
        float t = Mth.clamp(progress, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    private record EffectVertex(float x, float y, float u, float v) {
    }

    private record EffectFace(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, int side) {
        void line(float x1, float y1, float x2, float y2, float width, float u, float v, float brightness) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length < 1.0E-5F) {
                return;
            }
            float offsetX = -dy / length * width * 0.5F;
            float offsetY = dx / length * width * 0.5F;
            quad(brightness,
                    new EffectVertex(x1 + offsetX, y1 + offsetY, u, v),
                    new EffectVertex(x2 + offsetX, y2 + offsetY, u, v),
                    new EffectVertex(x2 - offsetX, y2 - offsetY, u, v),
                    new EffectVertex(x1 - offsetX, y1 - offsetY, u, v));
        }

        void quad(float brightness, EffectVertex a, EffectVertex b, EffectVertex c, EffectVertex d) {
            float z = side > 0 ? FRONT_Z : BACK_Z;
            vertex(a, z, brightness, side);
            vertex(b, z, brightness, side);
            vertex(c, z, brightness, side);
            vertex(d, z, brightness, side);
            vertex(d, z, brightness, -side);
            vertex(c, z, brightness, -side);
            vertex(b, z, brightness, -side);
            vertex(a, z, brightness, -side);
        }

        private void vertex(EffectVertex vertex, float z, float brightness, float normalZ) {
            // entityTranslucent 使用 NEW_ENTITY，手写顶点也需要 UV、overlay、light 和 normal。
            buffer.addVertex(pose, vertex.x(), vertex.y(), z)
                    .setColor(brightness, brightness, brightness, 1.0F)
                    .setUv(vertex.u(), vertex.v())
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, 0.0F, 0.0F, normalZ);
        }
    }
}
