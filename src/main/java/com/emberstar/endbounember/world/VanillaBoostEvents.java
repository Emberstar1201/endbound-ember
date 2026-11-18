package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * 原版经验掉落提升：生物死亡掉落的经验按倍率放大。
 * 由服务端配置 vanillaXpBoostEnabled 控制开关。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class VanillaBoostEvents {
    private VanillaBoostEvents() {
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!Config.VANILLA_XP_BOOST_ENABLED.get()) return;
        int original = event.getDroppedExperience();
        if (original <= 0) return;
        event.setDroppedExperience((int) Math.round(original * LootBoostModifier.MULTIPLIER));
    }
}
