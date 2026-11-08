package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;

@Mixin(Enchantment.class)
public abstract class EnchantmentCompatibilityMixin {
    @Inject(method = "areCompatible", at = @At("HEAD"), cancellable = true)
    private static void endboundEmber$allowVanillaEnchantmentsTogether(
            Holder<Enchantment> first, Holder<Enchantment> second, CallbackInfoReturnable<Boolean> cir) {
        if (Config.BETTER_LUCK_ENCHANTMENT.get()
                && isVanillaEnchantment(first)
                && isVanillaEnchantment(second)) {
            cir.setReturnValue(true);
        }
    }

    private static boolean isVanillaEnchantment(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey()
                .map(key -> "minecraft".equals(key.location().getNamespace()))
                .orElse(false);
    }
}
