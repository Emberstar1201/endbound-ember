package com.emberstar.endbounember.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 光环分层贴图的几何绘制。
 * <p>
 * 结构参考 Halo Mod（作者 Azusa_Ke，MIT License，https://github.com/AzusaKe/Halo）的做法：
 * 一个光环被拆成多张 PNG，每层独立设置尺寸、上下浮动相位与自旋速度，叠加后形成完整的像素光环。
 * 原始版权声明与许可证全文见项目根目录的 THIRD-PARTY-NOTICES.md。
 * <p>
 * 贴图位于 assets/endbound_ember/textures/halo/，可直接用图像编辑器修改。
 */
public final class HaloLayers {
    /** 图层贴图（由 tools/split_halo_texture.py 从单张光环贴图拆分而来） */
    public static final ResourceLocation LAYER_OUTER =
            ResourceLocation.fromNamespaceAndPath("endbound_ember", "textures/halo/halo_layer_0.png");
    public static final ResourceLocation LAYER_CORE =
            ResourceLocation.fromNamespaceAndPath("endbound_ember", "textures/halo/halo_layer_2.png");
    public static final ResourceLocation LAYER_INNER =
            ResourceLocation.fromNamespaceAndPath("endbound_ember", "textures/halo/halo_layer_3.png");

    /** 单个图层的配置：贴图、边长、旋转速度（度/ tick）、公转轨道半径、浮动相位与浮动幅度 */
    public record Layer(ResourceLocation texture, float size, float spin, float orbitRadius,
            float bobPhase, float bobAmplitude) {
    }

    /** 0号外环原地自转、2号中心星上下浮动 */
    public static final Layer[] LAYERS = {
            new Layer(LAYER_OUTER, 0.80F, 1.20F, 0.0F, 0.0F, 0.0F),
            new Layer(LAYER_CORE, 0.30F, 0.45F, 0.0F, 1.8F, 0.06F),
    };

    /** 3号外部星尘：在环上均匀分布的星点数量 */
    private static final int STAR_COUNT = 6;
    /** 星点边长 */
    private static final float STAR_SIZE = 0.13F;
    /** 星点公转轨道半径（位于 0 号外环之外） */
    private static final float STAR_ORBIT = 0.55F;
    /** 星点公转速度（度/ tick） */
    private static final float STAR_ORBIT_SPEED = 0.85F;
    /** 星点自身自转速度（度/ tick），与公转方向相反，视觉上保持朝向稳定 */
    private static final float STAR_SPIN_SPEED = -0.85F;

    /** 上下浮动角速度 */
    private static final float BOB_OMEGA = 0.11F;

    private HaloLayers() {
    }

    /**
     * 绘制全部图层。
     *
     * @param pose       已定位到环心、且已完成水平朝向的变换栈
     * @param buffers    渲染缓冲源
     * @param time       动画时间（tick，含部分刻）
     * @param brightness 亮度倍率，用于表现明亮 / 暗淡 / 破损
     * @param alpha      整体不透明度（0-255）
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, float time,
            float brightness, int alpha) {
        for (Layer layer : LAYERS) {
            float bob = Mth.sin(time * BOB_OMEGA + layer.bobPhase()) * layer.bobAmplitude();
            pose.pushPose();
            pose.translate(0.0D, bob, 0.0D);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(time * layer.spin()));
            VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(layer.texture()));
            drawQuad(pose, buffer, layer.size(), brightness, alpha);
            pose.popPose();
        }
        renderStars(pose, buffers, time, brightness, alpha);
    }

    /** 绘制环绕外环公转的星尘：每颗星点独立绕自身中心自转，整体沿轨道公转 */
    private static void renderStars(PoseStack pose, MultiBufferSource buffers, float time,
            float brightness, int alpha) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityTranslucent(LAYER_INNER));
        float orbit = time * STAR_ORBIT_SPEED;
        for (int i = 0; i < STAR_COUNT; i++) {
            float angle = orbit + i * (360.0F / STAR_COUNT);
            pose.pushPose();
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(angle));
            pose.translate(STAR_ORBIT, 0.0D, 0.0D);
            // 抵消公转带来的朝向变化，只保留星点自身缓慢自转
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-angle + time * STAR_SPIN_SPEED));
            drawQuad(pose, buffer, STAR_SIZE, brightness, alpha);
            pose.popPose();
        }
    }

    /** 绘制一张居中于原点的贴图四边形（水平平面，法线朝上） */
    private static void drawQuad(PoseStack pose, VertexConsumer buffer, float size, float brightness, int alpha) {
        Matrix4f matrix = pose.last().pose();
        float half = size / 2.0F;
        int light = 0xF000F0;   // 自发光：不受环境光影响
        int a = Mth.clamp(alpha, 0, 255);
        int c = Mth.clamp((int) (255 * brightness), 0, 255);
        // 四个角点，UV 与贴图方向一致（贴图上方对应 -Z）
        buffer.addVertex(matrix, -half, 0.0F, -half).setColor(c, c, c, a).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(matrix, half, 0.0F, -half).setColor(c, c, c, a).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(matrix, half, 0.0F, half).setColor(c, c, c, a).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
        buffer.addVertex(matrix, -half, 0.0F, half).setColor(c, c, c, a).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
    }
}
