package com.emberstar.endbounember.client;

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class MusicPlayerKeyClient {
    private static final KeyMapping KEY = new KeyMapping("key.endbound_ember.open_music_player",
            GLFW.GLFW_KEY_J, "key.categories.endbound_ember");

    private MusicPlayerKeyClient() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;
        while (KEY.consumeClick()) minecraft.setScreen(new MusicPlaylistScreen(null));
    }
}
