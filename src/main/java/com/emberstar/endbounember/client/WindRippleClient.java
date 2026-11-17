package com.emberstar.endbounember.client;

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.WindRipplePayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class WindRippleClient {
    public static final KeyMapping KEY = new KeyMapping(
            "key.endbound_ember.wind_ripple", GLFW.GLFW_KEY_KP_7, "key.categories.endbound_ember");

    private static boolean wasDown;

    private WindRippleClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !Config.WIND_RIPPLE_ENABLED.get()) {
            wasDown = false;
            return;
        }

        boolean down = KEY.isDown();
        if (down && !wasDown) {
            PacketDistributor.sendToServer(new WindRipplePayload(true));
        }
        wasDown = down;
    }
}
