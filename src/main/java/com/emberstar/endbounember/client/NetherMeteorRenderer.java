package com.emberstar.endbounember.client;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.NetherMeteor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** 下界流星的渲染器：按坠落进度把模型从高空推到落点，并叠一层自发光裂纹。 */
public class NetherMeteorRenderer extends EntityRenderer<NetherMeteor> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "textures/entity/nether_meteor.png");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "textures/entity/nether_meteor_glow.png");
    /** 岩块自转速度（度 / tick） */
    private static final float SPIN_SPEED = 14.0F;
    /** 岩块固定的倾角，让坠落姿态不那么“正” */
    private static final float TILT_X = 26.0F;
    private static final float TILT_Z = 18.0F;

    private final NetherMeteorModel model;

    public NetherMeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new NetherMeteorModel(context.bakeLayer(NetherMeteorModel.LAYER));
        // 高空中的火球不需要地面投影
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(NetherMeteor entity, float entityYaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight) {
        float progress = entity.fallProgress(partialTick);
        double height = NetherMeteor.FALL_HEIGHT * (1.0D - progress);
        pose.pushPose();
        pose.translate(0.0D, height + 0.45D, 0.0D);
        pose.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * SPIN_SPEED));
        pose.mulPose(Axis.XP.rotationDegrees(TILT_X));
        pose.mulPose(Axis.ZP.rotationDegrees(TILT_Z));
        // 越接近地面越大，强化压迫感
        float scale = 0.8F + 0.5F * progress;
        pose.scale(scale, scale, scale);
        this.model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        this.model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(GLOW)),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(NetherMeteor entity) {
        return TEXTURE;
    }
}
