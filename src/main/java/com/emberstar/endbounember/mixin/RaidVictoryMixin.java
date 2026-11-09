package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.world.BetterVillagerTrading;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Raid.class)
public abstract class RaidVictoryMixin {
    @Shadow public abstract boolean isVictory();
    @Shadow public abstract net.minecraft.core.BlockPos getCenter();
    @Shadow private ServerLevel level;

    @Unique private boolean endboundEmber$discountAwarded;

    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void endboundEmber$loadAwardState(ServerLevel level, CompoundTag tag, CallbackInfo ci) {
        this.endboundEmber$discountAwarded = tag.getBoolean("EndboundEmberDiscountAwarded");
    }

    @Inject(method = "save", at = @At("TAIL"))
    private void endboundEmber$saveAwardState(CompoundTag tag, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<CompoundTag> cir) {
        tag.putBoolean("EndboundEmberDiscountAwarded", this.endboundEmber$discountAwarded);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void endboundEmber$ensureDiscountAwarded(CallbackInfo ci) {
        if (Config.BETTER_VILLAGER_TRADING.get() && this.isVictory() && !this.endboundEmber$discountAwarded) {
            this.endboundEmber$discountAwarded = true;
            BetterVillagerTrading.awardVillageDiscount(this.level, this.getCenter());
        }
    }
}
