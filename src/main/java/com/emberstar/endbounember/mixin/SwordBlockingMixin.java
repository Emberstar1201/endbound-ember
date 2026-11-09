package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.world.SwordBlocking;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * 给剑补上「可蓄力」与「格挡动作」，让剑能像盾牌一样举起来格挡。
 * <p>只改这两个只读方法，不碰其它逻辑；关闭配置后原样返回。</p>
 */
@Mixin(Item.class)
public abstract class SwordBlockingMixin {
    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    private void endbound$swordUseDuration(ItemStack stack, LivingEntity entity,
            CallbackInfoReturnable<Integer> cir) {
        if (Config.SWORD_BLOCKING_ENABLED.get() && SwordBlocking.isBlockingSword(stack)) {
            cir.setReturnValue(SwordBlocking.USE_DURATION);
        }
    }

    @Inject(method = "getUseAnimation", at = @At("HEAD"), cancellable = true)
    private void endbound$swordUseAnimation(ItemStack stack, CallbackInfoReturnable<UseAnim> cir) {
        if (Config.SWORD_BLOCKING_ENABLED.get() && SwordBlocking.isBlockingSword(stack)) {
            cir.setReturnValue(UseAnim.BLOCK);
        }
    }
}
