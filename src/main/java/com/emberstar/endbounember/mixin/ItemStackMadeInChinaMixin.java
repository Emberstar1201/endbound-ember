package com.emberstar.endbounember.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.emberstar.endbounember.enchantment.MadeInChinaEnchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 中国制造：在真正扣除耐久之前判定，命中概率时整次损耗被取消。
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMadeInChinaMixin {
    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void endboundEmber$ignoreDurability(int amount, ServerLevel level, LivingEntity entity,
            Consumer<Item> onBreak, CallbackInfo ci) {
        if (amount <= 0) return;

        ItemStack stack = (ItemStack) (Object) this;
        if (MadeInChinaEnchantment.ignoresDurability(stack, level.getRandom())) {
            ci.cancel();
        }
    }
}
