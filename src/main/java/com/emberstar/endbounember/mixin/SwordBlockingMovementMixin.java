package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.emberstar.endbounember.world.SwordBlocking;

import net.minecraft.client.player.LocalPlayer;

/**
 * 剑格挡期间不减速：原版“使用物品”状态会把移动输入压到 20% 并禁止起跑，
 * 这里只对剑格挡放行，盾牌、弓、食物等仍保持原版手感。
 */
@Mixin(LocalPlayer.class)
public abstract class SwordBlockingMovementMixin {

    @Redirect(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean endbound$keepWalkingWhileBlocking(LocalPlayer player) {
        return player.isUsingItem() && !SwordBlocking.isBlockingSword(player.getUseItem());
    }

    @Redirect(
            method = "canStartSprinting",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean endbound$allowSprintWhileBlocking(LocalPlayer player) {
        return player.isUsingItem() && !SwordBlocking.isBlockingSword(player.getUseItem());
    }
}
