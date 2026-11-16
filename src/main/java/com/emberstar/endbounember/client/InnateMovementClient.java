package com.emberstar.endbounember.client;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

@EventBusSubscriber(modid = "endbound_ember", value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class InnateMovementClient {
    private InnateMovementClient() {
    }

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        if (!Config.WIND_RIPPLE_ENABLED.get()) return;
        ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "wind_ripple_speed");
        AttributeModifier modifier = event.getPlayer().getAttribute(Attributes.MOVEMENT_SPEED).getModifier(modifierId);
        if (modifier == null) return;
        event.setNewFovModifier(event.getFovModifier() / (float) (1.0D + modifier.amount()));
    }
}
