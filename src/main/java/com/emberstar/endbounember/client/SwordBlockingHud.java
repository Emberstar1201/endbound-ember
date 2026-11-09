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
    private static final int PARRY_WINDOW_TICKS = 10;

    private SwordBlockingHud() {
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!Config.SWORD_BLOCKING_ENABLED.get() || player == null || minecraft.options.hideGui
                || minecraft.screen != null || player.isSpectator()
                || !player.isUsingItem() || !SwordBlocking.isBlockingSword(player.getUseItem())) {
            return;
        }

        float progress = 1.0F - Math.min(player.getTicksUsingItem(), PARRY_WINDOW_TICKS) / (float) PARRY_WINDOW_TICKS;
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
}
