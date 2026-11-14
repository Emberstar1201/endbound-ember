package com.emberstar.endbounember.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.emberstar.endbounember.Config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

@Mixin(ItemStack.class)
public abstract class ItemStackAutoReplaceMixin {
    @Inject(
            method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void endboundEmber$skipDurability(int amount, ServerLevel level, LivingEntity owner,
            Consumer<Item> onBreak, CallbackInfo ci) {
        if (amount <= 0 || !Config.AUTO_REPLACE_ENABLED.get() || !(owner instanceof Player player)
                || level.isClientSide || player.getMainHandItem() != (Object) this
                || !((ItemStack) (Object) this).isDamageableItem()) return;

        Item item = ((ItemStack) (Object) this).getItem();
        int selected = player.getInventory().selected;
        for (int slot = 0; slot < 36; slot++) {
            if (slot == selected) continue;
            ItemStack candidate = player.getInventory().getItem(slot);
            if (candidate.isEmpty() || candidate.getItem() != item || !candidate.isDamageableItem()
                    || candidate.getDamageValue() >= candidate.getMaxDamage()) continue;

            ItemStack replacement = candidate.split(1);
            player.getInventory().setItem(selected, replacement);
            ci.cancel();
            return;
        }
    }
}
