package com.emberstar.endbounember.client;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.block.DisplayPedestalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 展示台渲染器：把展示中的物品悬浮在台面中心上方，缓慢自转并上下浮动。
 * <p>
 * 仪式进行中时，物品会加速旋转、放大并升起，配合服务端粒子表现星尘凝聚的过程。
 */
public class DisplayPedestalRenderer implements BlockEntityRenderer<DisplayPedestalBlockEntity> {
    /** 物品悬浮高度（台面顶部 1.0 格之上再抬高） */
    private static final float HOVER_HEIGHT = 1.35F;
    /** 上下浮动幅度（格） */
    private static final float BOB_AMPLITUDE = 0.08F;
    /** 浮动角速度 */
    private static final float BOB_OMEGA = 0.08F;
    /** 自转速度（度/ tick） */
    private static final float SPIN_SPEED = 1.2F;
    /** 仪式期间额外升起的高度 */
    private static final float RITUAL_RISE = 0.55F;
    /** 仪式期间额外放大的倍数 */
    private static final float RITUAL_SCALE = 0.45F;

    private final ItemRenderer itemRenderer;

    public DisplayPedestalRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(DisplayPedestalBlockEntity pedestal, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ItemStack stack = pedestal.getStoredItem();
        if (stack.isEmpty()) {
            return;
        }
        Level level = pedestal.getLevel();
        if (level == null) {
            return;
        }

        // 用方块实体自己的动画时间，玩家远离时该时间停止推进，动画随之冻结
        double time = pedestal.getAnimationTime() + partialTick;

        // 仪式进度 0~1，未进行时为 0
        int elapsed = pedestal.getRitualElapsed(partialTick);
        float progress = elapsed < 0 ? 0.0F
                : Mth.clamp(elapsed / (float) EndboundEmber.HALO_RITUAL_DURATION, 0.0F, 1.0F);
        // 前半段蓄力、后半段释放，用正弦曲线让升降与缩放更平滑
        float ritualCurve = Mth.sin(progress * Mth.PI);

        float bob = Mth.sin((float) (time * BOB_OMEGA)) * BOB_AMPLITUDE;
        float spinSpeed = SPIN_SPEED + progress * 14.0F;
        float scale = 0.75F + ritualCurve * RITUAL_SCALE;

        pose.pushPose();
        pose.translate(0.5D, HOVER_HEIGHT + bob + ritualCurve * RITUAL_RISE, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees((float) (time * spinSpeed) % 360.0F));
        // 物品略微前倾，避免完全平放时看不出立体感
        pose.mulPose(Axis.XP.rotationDegrees(20.0F - ritualCurve * 20.0F));
        pose.scale(scale, scale, scale);

        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
    }
}
