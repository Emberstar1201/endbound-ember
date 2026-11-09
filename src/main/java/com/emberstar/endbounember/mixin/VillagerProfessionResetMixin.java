package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.world.BetterVillagerTrading;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerProfessionResetMixin {
    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void endboundEmber$resetMissingProfessionSite(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (villager.tickCount % 100 == 0) BetterVillagerTrading.resetProfessionIfNoJobSite(villager);
    }
}
