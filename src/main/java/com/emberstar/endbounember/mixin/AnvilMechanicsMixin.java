package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AnvilMenu.class)
public abstract class AnvilMechanicsMixin {
    @Shadow private String itemName;
    @Inject(method = "createResult", at = @At("RETURN"))
    private void endboundEmber$makeRenameFree(CallbackInfo ci) {
        if (!Config.ANVIL_MECHANICS_ENABLED.get()) return;
        AnvilMenu menu = (AnvilMenu) (Object) this;
        if (!menu.getSlot(2).getItem().isEmpty() && endboundEmber$isRenameOnly(menu)) {
            menu.setMaximumCost(0);
            menu.broadcastChanges();
        }
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void endboundEmber$allowFreeRename(Player player, boolean hasStack,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (!Config.ANVIL_MECHANICS_ENABLED.get()) return;
        AnvilMenu menu = (AnvilMenu) (Object) this;
        if (hasStack && endboundEmber$isRenameOnly(menu)) {
            cir.setReturnValue(true);
        }
    }

    @ModifyConstant(method = "createResult", constant = @Constant(intValue = 40, ordinal = 1))
    private int endboundEmber$removeTooExpensiveLimit(int limit) {
        return Config.ANVIL_MECHANICS_ENABLED.get() ? Integer.MAX_VALUE : limit;
    }

    @Redirect(
            method = "onTake",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;giveExperienceLevels(I)V"))
    private void endboundEmber$skipRenameExperienceCost(Player player, int levels) {
        AnvilMenu menu = (AnvilMenu) (Object) this;
        boolean freeRename = Config.ANVIL_MECHANICS_ENABLED.get() && endboundEmber$isRenameOnly(menu);
        player.giveExperienceLevels(freeRename ? 0 : levels);
    }

    @Redirect(
            method = "onTake",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/common/CommonHooks;onAnvilRepair(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)F"))
    private float endboundEmber$reduceAnvilDamageChance(Player player, ItemStack result,
                                                        ItemStack left, ItemStack right) {
        float damageChance = CommonHooks.onAnvilRepair(player, result, left, right);
        return Config.ANVIL_MECHANICS_ENABLED.get() ? damageChance * 0.05F : damageChance;
    }

    @Unique
    private boolean endboundEmber$isRenameOnly(AnvilMenu menu) {
        ItemStack input = menu.getSlot(0).getItem();
        if (input.isEmpty() || !menu.getSlot(1).getItem().isEmpty()) return false;
        String newName = this.itemName;
        if (newName != null && !StringUtil.isBlank(newName)) {
            return !newName.equals(input.getHoverName().getString());
        }
        return input.has(DataComponents.CUSTOM_NAME);
    }

}
