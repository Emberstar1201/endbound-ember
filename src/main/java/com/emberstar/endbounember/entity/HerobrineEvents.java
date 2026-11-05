package com.emberstar.endbounember.entity;

import com.emberstar.endbounember.EndboundEmber;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Herobrine 的属性注册。
 * <p>注册前先把原版生命值属性上限放开，1500 点血量才会生效。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class HerobrineEvents {
    private HerobrineEvents() {
    }

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        Herobrine.raiseMaxHealthCap();
        event.put(ModEntities.HEROBRINE.get(), Herobrine.createAttributes().build());
    }
}
