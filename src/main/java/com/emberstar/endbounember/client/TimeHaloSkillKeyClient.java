package com.emberstar.endbounember.client;

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.TimeHaloSkillPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 时光光环的三个技能键：小键盘 1 变慢、2 暂停、3 倒流（均可自定义）。 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class TimeHaloSkillKeyClient {
    public static final KeyMapping SLOW_KEY = new KeyMapping(
            "key.endbound_ember.time_halo_slow", GLFW.GLFW_KEY_KP_1, "key.categories.endbound_ember");
    public static final KeyMapping STOP_KEY = new KeyMapping(
            "key.endbound_ember.time_halo_stop", GLFW.GLFW_KEY_KP_2, "key.categories.endbound_ember");
    public static final KeyMapping REWIND_KEY = new KeyMapping(
            "key.endbound_ember.time_halo_rewind", GLFW.GLFW_KEY_KP_3, "key.categories.endbound_ember");

    private TimeHaloSkillKeyClient() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(SLOW_KEY);
        event.register(STOP_KEY);
        event.register(REWIND_KEY);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) return;
        while (SLOW_KEY.consumeClick()) {
            PacketDistributor.sendToServer(new TimeHaloSkillPayload(TimeHaloSkillPayload.SLOW));
        }
        while (STOP_KEY.consumeClick()) {
            PacketDistributor.sendToServer(new TimeHaloSkillPayload(TimeHaloSkillPayload.STOP));
        }
        while (REWIND_KEY.consumeClick()) {
            PacketDistributor.sendToServer(new TimeHaloSkillPayload(TimeHaloSkillPayload.REWIND));
        }
    }
}
