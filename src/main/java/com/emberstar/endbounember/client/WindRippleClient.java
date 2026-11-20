package com.emberstar.endbounember.client;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.WindRipplePayload;
import com.emberstar.endbounember.world.InnateMovement;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 风踏涟漪开关：直接复用原版潜行键（Shift）的按下动作，不再单独占用一个按键。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class WindRippleClient {
    private static boolean wasDown;
    /** 客户端自己的开关状态；服务端只记录同一份结果。 */
    private static boolean enabled;

    private WindRippleClient() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !Config.WIND_RIPPLE_ENABLED.get()) {
            wasDown = false;
            return;
        }

        // 读取潜行键的实际绑定值，玩家改键后依然有效。
        boolean down = minecraft.options.keyShift.isDown();
        // 按下瞬间翻转本地状态并把结果发给服务端，保证两端一致。
        if (down && !wasDown) {
            enabled = !enabled;
            PacketDistributor.sendToServer(new WindRipplePayload(enabled));
        }
        wasDown = down;
    }

    /**
     * 本地玩家每 tick 结束时贴回流体表面。
     * <p>服务端改的坐标会被客户端上报的位置覆盖，所以贴水只能在客户端做。</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        if (!enabled || !Config.WIND_RIPPLE_ENABLED.get()) return;
        if (player.isFallFlying() || player.isPassenger() || player.getAbilities().flying) return;
        InnateMovement.applySurfaceClient(player);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        enabled = false;
        wasDown = false;
    }
}
