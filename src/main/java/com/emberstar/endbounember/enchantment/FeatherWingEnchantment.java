package com.emberstar.endbounember.enchantment;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 羽翼配置开启时，玩家免疫鞘翅飞行撞墙伤害。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class FeatherWingEnchantment {
    private FeatherWingEnchantment() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!Config.FEATHER_WING_ENABLED.get() || event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (!event.getSource().is(DamageTypes.FLY_INTO_WALL)) return;
        event.setCanceled(true);
    }
}
