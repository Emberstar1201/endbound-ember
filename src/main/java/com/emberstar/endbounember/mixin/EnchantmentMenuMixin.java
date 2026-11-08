package com.emberstar.endbounember.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.emberstar.endbounember.enchantment.EnchantingTableRules;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    @Redirect(
            method = "slotsChanged(Lnet/minecraft/world/Container;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isEnchantable()Z"))
    private boolean endboundEmber$allowAlreadyEnchantedItems(ItemStack stack) {
        return EnchantingTableRules.canUse(stack);
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void endboundEmber$validateInput(Player player, int id, CallbackInfoReturnable<Boolean> cir) {
        EnchantmentMenu menu = (EnchantmentMenu) (Object) this;
        if (id >= 0 && id < 3 && !EnchantingTableRules.canUse(menu.getSlot(0).getItem())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getEnchantmentList", at = @At("RETURN"), cancellable = true)
    private void endboundEmber$limitCandidates(RegistryAccess registryAccess, ItemStack stack, int slot, int cost,
            CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        cir.setReturnValue(EnchantingTableRules.limitCandidates(stack, cir.getReturnValue()));
    }
}
