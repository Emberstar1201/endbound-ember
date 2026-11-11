package com.emberstar.endbounember.client;

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * C 键放大：按住放大键时平滑拉近视野，松开后恢复。
 * <p>放大键可在按键绑定里自由修改，整体功能由配置项 zoomEnabled 控制。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class ZoomClient {
    public static final KeyMapping ZOOM_KEY = new KeyMapping(
            "key.endbound_ember.zoom", GLFW.GLFW_KEY_C, "key.categories.endbound_ember");

    /** 当前实际使用的视野倍率，用于平滑过渡。 */
    private static double currentZoom = 1.0D;

    private ZoomClient() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(ZOOM_KEY);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean zooming = Config.ZOOM_ENABLED.get()
                && minecraft.player != null
                && minecraft.screen == null
                && ZOOM_KEY.isDown();

        double target = zooming ? Config.ZOOM_FOV_MULTIPLIER.get() : 1.0D;
        if (Config.ZOOM_TRANSITION_ENABLED.get()) {
            double speed = Config.ZOOM_TRANSITION_SPEED.get();
            currentZoom += (target - currentZoom) * speed;
            if (Math.abs(target - currentZoom) < 0.001D) {
                currentZoom = target;
            }
        } else {
            currentZoom = target;
        }

        event.setFOV(event.getFOV() * currentZoom);
    }
}
