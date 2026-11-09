package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.world.BetterVillagerTrading;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class FoolVillagerTradesMixin {
    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void endboundEmber$addFoolTrades(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (!Config.BETTER_VILLAGER_TRADING.get()
                || !villager.getPersistentData().getBoolean(BetterVillagerTrading.FOOL_KEY)) return;
        BetterVillagerTrading.addFoolTrades(villager);
    }
}
