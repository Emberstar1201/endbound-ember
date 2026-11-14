package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Mixin(LivingEntity.class)
public abstract class LivingEntityInventoryTotemMixin {
    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void endboundEmber$checkInventoryTotem(net.minecraft.world.damagesource.DamageSource damageSource,
            CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof Player player) || player.level().isClientSide
                || damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !Config.INVENTORY_TOTEM_ENABLED.get()) return;

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.is(Items.TOTEM_OF_UNDYING) || held.is(EndboundEmber.ETERNAL_TOTEM.get())) return;
        }

        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                ItemStack consumed = stack.copyWithCount(1);
                stack.shrink(1);
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
                    CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, consumed);
                }
                entity.setHealth(1.0F);
                entity.removeEffectsCuredBy(net.neoforged.neoforge.common.EffectCures.PROTECTED_BY_TOTEM);
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
                entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
                entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
                player.level().broadcastEntityEvent(player, (byte) 35);
                cir.setReturnValue(true);
                return;
            }
            if (stack.is(EndboundEmber.ETERNAL_TOTEM.get())) {
                stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                entity.setHealth(entity.getMaxHealth());
                entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 40 * 20, 3));
                entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40 * 20, 2));
                entity.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40 * 20, 0));
                entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40 * 20, 4));
                player.level().broadcastEntityEvent(player, (byte) 35);
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
