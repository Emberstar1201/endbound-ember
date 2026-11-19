package com.emberstar.endbounember.client;

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.ChainMiningRequestPayload;
import com.emberstar.endbounember.network.MovementSpeedAdjustPayload;
import com.emberstar.endbounember.world.MovementSpeedState;

import com.mojang.blaze3d.platform.InputConstants;

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
            while (TOGGLE_KEY.consumeClick()) { }
            return;
        }
        while (TOGGLE_KEY.consumeClick()) PacketDistributor.sendToServer(new ChainMiningRequestPayload(false));
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || event.getScrollDeltaY() == 0.0D) return;
        long window = minecraft.getWindow().getWindow();
        // 检测字面左右 Ctrl，避免 macOS 的命令键替代；Ctrl 优先于 Shift 连锁采集。
        boolean controlDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        if (controlDown) {
            int skill = MovementSpeedState.activeSkill(minecraft.player);
            if (skill >= 0) {
                PacketDistributor.sendToServer(new MovementSpeedAdjustPayload(skill, event.getScrollDeltaY() > 0.0D));
                event.setCanceled(true);
            }
            return;
        }
        if (!Screen.hasShiftDown()) return;
        PacketDistributor.sendToServer(new ChainMiningRequestPayload(true));
        event.setCanceled(true);
    }
}
