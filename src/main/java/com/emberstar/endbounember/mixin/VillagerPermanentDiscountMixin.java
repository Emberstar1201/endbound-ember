package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.world.BetterVillagerTrading;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerPermanentDiscountMixin {
    @Inject(method = "updateSpecialPrices", at = @At("TAIL"))
    private void endboundEmber$applyPermanentDiscount(Player player, CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (!Config.BETTER_VILLAGER_TRADING.get()) return;
        int percent = BetterVillagerTrading.discount(villager);
        if (percent <= 0) return;
        for (MerchantOffer offer : villager.getOffers()) {
            int vanillaPrice = offer.getBaseCostA().getCount();
            int targetPrice = Math.max(1, (int) Math.floor(vanillaPrice * (100 - percent) / 100.0D));
            int desiredDiff = targetPrice - vanillaPrice;
            // 只压低到折扣目标价，保留与原版声望折扣中更便宜的那个结果；重复调用也不会叠加。
            if (offer.getSpecialPriceDiff() > desiredDiff) {
                offer.setSpecialPriceDiff(desiredDiff);
            }
        }
    }
}
