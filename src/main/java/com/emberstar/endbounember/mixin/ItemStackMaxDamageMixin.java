package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.emberstar.endbounember.enchantment.DurabilityBoostEnchantment;

import net.minecraft.world.item.ItemStack;

/**
 * 耐久强化：在读取最大耐久时按附魔等级放大，基础值仍保存在 MAX_DAMAGE 组件中，
 * 因此附魔被移除后耐久上限会自然恢复，也不会写入多余的持久化数据。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMaxDamageMixin {
    @Inject(method = "getMaxDamage", at = @At("RETURN"), cancellable = true)
    private void endboundEmber$applyDurabilityBoost(CallbackInfoReturnable<Integer> cir) {
        int base = cir.getReturnValue();
        if (base <= 0) return;

        int level = DurabilityBoostEnchantment.levelOf((ItemStack) (Object) this);
        if (level <= 0) return;

        cir.setReturnValue(DurabilityBoostEnchantment.boostedMaxDamage(base, level));
    }
}
