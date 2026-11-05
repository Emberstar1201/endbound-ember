package com.emberstar.endbounember.client;

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.TimeTurbulenceLevels;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/**
 * 佩戴时间光环后，按 I（可在按键绑定里改）打开维度传送轮盘。
 * <p>没有光环但被卷进时间乱流的玩家，也可以按 I 打开轮盘离开。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class TimeTurbulenceKeyClient {
    public static final KeyMapping TOGGLE_KEY = new KeyMapping(
            "key.endbound_ember.time_turbulence_toggle", GLFW.GLFW_KEY_I, "key.categories.endbound_ember");

    private TimeTurbulenceKeyClient() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) return;
        // 客户端只检查头盔槽（Curios 饰品栏需要服务端校验）；服务端 payload handler 仍会严格把关。
        // 没有光环但身处时间乱流的玩家，同样允许打开轮盘以便离开。
        boolean hasHalo = EndboundEmber.TIME_HALO.get()
                .equals(minecraft.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem());
        boolean inTurbulence = TimeTurbulenceLevels.isTimeTurbulence(minecraft.player.level());
        if (!hasHalo && !inTurbulence) {
            return;
        }
        while (TOGGLE_KEY.consumeClick()) {
            minecraft.setScreen(new TimeDimensionWheelScreen());
        }
    }
}
