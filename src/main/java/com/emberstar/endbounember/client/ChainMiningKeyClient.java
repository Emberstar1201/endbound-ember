package com.emberstar.endbounember.client;

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.ChainMiningRequestPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class ChainMiningKeyClient {
    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.endbound_ember.chain_mining_toggle", GLFW.GLFW_KEY_GRAVE_ACCENT, "key.categories.endbound_ember");

    private ChainMiningKeyClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            while (TOGGLE_KEY.consumeClick()) {
            }
            return;
        }
        while (TOGGLE_KEY.consumeClick()) PacketDistributor.sendToServer(new ChainMiningRequestPayload(false));
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !Screen.hasShiftDown() || event.getScrollDeltaY() == 0.0D) return;
        PacketDistributor.sendToServer(new ChainMiningRequestPayload(true));
        event.setCanceled(true);
    }
}
