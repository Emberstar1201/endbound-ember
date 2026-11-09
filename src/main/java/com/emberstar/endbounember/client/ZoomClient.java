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

    /** 放大后的视野倍率，0.25 相当于 4 倍放大。 */
    private static final double ZOOM_FOV = 0.25D;
    /** 每帧靠近目标倍率的比例，越大越干脆。 */
    private static final double ZOOM_SPEED = 0.35D;

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

        double target = zooming ? ZOOM_FOV : 1.0D;
        currentZoom += (target - currentZoom) * ZOOM_SPEED;
        if (Math.abs(target - currentZoom) < 0.001D) {
            currentZoom = target;
        }

        event.setFOV(event.getFOV() * currentZoom);
    }
}
