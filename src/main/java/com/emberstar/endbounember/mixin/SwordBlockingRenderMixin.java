package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.world.SwordBlocking;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 剑格挡的第一人称动画。
 * <p>姿势数值照搬 Sword Blocking Mechanics（作者 Fuzss，MPL-2.0）的
 * {@code FirstPersonRenderingHandler#transformBlockFirstPerson}，见
 * https://github.com/Fuzss/sword-blocking-mechanics 。原版 BLOCK 动作在第一人称只是把手放回原位，
 * 剑和平时没区别；这里接管整条渲染链，让剑横在身前、剑面朝屏幕。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class SwordBlockingRenderMixin {

    @Shadow
    private void applyItemArmTransform(PoseStack poseStack, HumanoidArm hand, float equippedProgress) {
        throw new AssertionError();
    }

    @Shadow
    private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm hand, float swingProgress) {
        throw new AssertionError();
    }

    /** 开始格挡时不要播原版的“抬起物品”补给动画，否则剑会先上下抽动一下。 */
    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void endbound$skipReequipForBlockingSword(InteractionHand hand, CallbackInfo ci) {
        if (!Config.SWORD_BLOCKING_ENABLED.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!player.isUsingItem() || player.getUsedItemHand() != hand) return;
        if (!SwordBlocking.isBlockingSword(player.getUseItem())) return;
        ci.cancel();
    }

    /** 用参考模组的变换整体替换格挡手的第一人称渲染。 */
    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private void endbound$renderBlockingSword(
            AbstractClientPlayer player,
            float partialTicks,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack stack,
            float equippedProgress,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int combinedLight,
            CallbackInfo ci) {
        if (!Config.SWORD_BLOCKING_ENABLED.get() || player.isScoping()) return;
        if (!player.isUsingItem() || player.getUsedItemHand() != hand) return;
        if (!SwordBlocking.isBlockingSword(stack)) return;

        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean rightArm = arm == HumanoidArm.RIGHT;

        poseStack.pushPose();
        this.applyItemArmTransform(poseStack, arm, equippedProgress);
        this.applyItemArmAttackTransform(poseStack, arm, swingProgress);
        transformBlockFirstPerson(poseStack, arm);
        ((ItemInHandRenderer) (Object) this).renderItem(
                player,
                stack,
                rightArm ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !rightArm,
                poseStack,
                buffer,
                combinedLight);
        poseStack.popPose();
        ci.cancel();
    }

    /** 数值取自 Sword Blocking Mechanics（MPL-2.0），来自 Minecraft 15w33b 快照的盾牌姿态。 */
    private static void transformBlockFirstPerson(PoseStack poseStack, HumanoidArm arm) {
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(direction * -0.14142136F, 0.08F, 0.14142136F);
        poseStack.mulPose(Axis.XP.rotationDegrees(-102.25F));
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * 13.365F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(direction * 78.05F));
    }
}
