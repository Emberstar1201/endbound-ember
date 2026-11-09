package com.emberstar.endbounember.client;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.SwordBlocking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** 正在格挡时于准星下方显示短暂的格挡窗口指示条。 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class SwordBlockingHud {
    private static final long PARRY_WINDOW_NANOS = SwordBlocking.PARRY_WINDOW_TICKS * 50_000_000L;
    /** 完美格挡成功的时间点，0 表示当前没有回满状态 */
    private static long parryRestartNanos;

    private SwordBlockingHud() {
    }

    /** 完美格挡成功：格挡条重新回满并从头计时。 */
    public static void onPerfectParry() {
        parryRestartNanos = System.nanoTime();
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!Config.SWORD_BLOCKING_ENABLED.get() || player == null || minecraft.options.hideGui
                || minecraft.screen != null || player.isSpectator()
                || !player.isUsingItem() || !SwordBlocking.isBlockingSword(player.getUseItem())) {
            parryRestartNanos = 0L;
            return;
        }

        float progress = parryProgress();
        if (progress <= 0.0F) {
            progress = 1.0F - Math.min(player.getTicksUsingItem(), SwordBlocking.PARRY_WINDOW_TICKS)
                    / (float) SwordBlocking.PARRY_WINDOW_TICKS;
        }
        if (progress <= 0.0F) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
        int top = minecraft.getWindow().getGuiScaledHeight() / 2 + 12;
        int width = 28;
        int filled = Math.round(width * progress);
        graphics.fill(centerX - width / 2 - 1, top - 1, centerX + width / 2 + 1, top + 4, 0xB0000000);
        graphics.fill(centerX - width / 2, top, centerX - width / 2 + filled, top + 2, 0xFFFFD56A);
    }

    /** 完美格挡后的格挡条进度；回满窗口结束后交给正常计时。 */
    private static float parryProgress() {
        if (parryRestartNanos == 0L) return 0.0F;
        long elapsed = System.nanoTime() - parryRestartNanos;
        if (elapsed >= PARRY_WINDOW_NANOS) {
            parryRestartNanos = 0L;
            return 0.0F;
        }
        return 1.0F - elapsed / (float) PARRY_WINDOW_NANOS;
    }
}
