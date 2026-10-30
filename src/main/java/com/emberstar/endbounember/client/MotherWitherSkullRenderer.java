package com.emberstar.endbounember.client;

import com.emberstar.endbounember.entity.MotherWitherSkull;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 母凋零之首的渲染器：直接借用原版凋零头颅的物品模型放大显示。
 */
public class MotherWitherSkullRenderer extends EntityRenderer<MotherWitherSkull> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/wither/wither.png");
    private static final ItemStack SKULL_ICON = new ItemStack(Items.WITHER_SKELETON_SKULL);
    private static final float SCALE = 2.4F;

    private final net.minecraft.client.renderer.entity.ItemRenderer itemRenderer;

    public MotherWitherSkullRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(MotherWitherSkull entity, float entityYaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight) {
        pose.pushPose();
        pose.scale(SCALE, SCALE, SCALE);
        this.itemRenderer.renderStatic(SKULL_ICON, ItemDisplayContext.GROUND, packedLight,
                OverlayTexture.NO_OVERLAY, pose, buffers, entity.level(), entity.getId());
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MotherWitherSkull entity) {
        return TEXTURE;
    }
}
