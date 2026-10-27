package com.emberstar.endbounember.client;

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.ChainBreakerPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 连锁挖掘触发键：按住 ~ 开启连锁模式，松开取消。 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class ChainBreakerClient {
    public static final KeyMapping KEY =
            new KeyMapping("key.endbound_ember.chain_breaker", GLFW.GLFW_KEY_GRAVE_ACCENT,
                    "key.categories.endbound_ember");

    /** 上一次同步给服务端的按键状态。 */
    private static boolean lastPressed = false;

    private ChainBreakerClient() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        // 打开界面时视为松开，避免状态残留在服务端
        boolean pressed = minecraft.screen == null && minecraft.player != null && KEY.isDown();
        if (pressed == lastPressed) return;

        lastPressed = pressed;
        PacketDistributor.sendToServer(new ChainBreakerPayload(pressed));
    }
}
