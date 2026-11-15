package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;

@Mixin(ItemEntity.class)
public abstract class ItemEntityCactusDamageMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void endboundEmber$protectDawnArmorFromCactus(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> cir) {
        ItemEntity itemEntity = (ItemEntity) (Object) this;
        if (source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)
                && (itemEntity.getItem().is(EndboundEmber.DAWN_HELMET.get())
                        || itemEntity.getItem().is(EndboundEmber.DAWN_CHESTPLATE.get())
                        || itemEntity.getItem().is(EndboundEmber.DAWN_LEGGINGS.get())
                        || itemEntity.getItem().is(EndboundEmber.DAWN_BOOTS.get()))) {
            cir.setReturnValue(false);
        }
    }
}
