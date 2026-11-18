package com.emberstar.endbounember.client;

import com.emberstar.endbounember.EndboundEmber;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class ChainMiningHud {
    private ChainMiningHud() {
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null) return;
        String key = ChainMiningClientState.enabled()
                ? "hud.endbound_ember.chain_mining.enabled"
                : "hud.endbound_ember.chain_mining.disabled";
        Component status = Component.translatable(key,
                Component.translatable("hud.endbound_ember.chain_mining.mode." + ChainMiningClientState.mode().name().toLowerCase()).getString());
        event.getGuiGraphics().drawString(minecraft.font, status, 8, 8, 0xFFFFFFFF, true);
    }
}
