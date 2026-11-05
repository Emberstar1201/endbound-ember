package com.emberstar.endbounember.client;

import java.util.Random;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.effect.ModEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * 时光光环技能的屏幕表现与状态提示。
 *
 * <p>时间变慢：整屏淡蓝滤镜、边缘暗角、快速掠过的横向拖影与移动扫描线。</p>
 * <p>时间暂停：更冷的冰蓝色蒙版与静止暗角。</p>
 * <p>两种状态都会在快捷栏上方显示当前模式与简要说明。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class TimeHaloSkillHud {

    private TimeHaloSkillHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || minecraft.screen != null || player.isSpectator()) {
            return;
        }
        boolean stopped = player.hasEffect(ModEffects.TIME_STOP);
        boolean slowed = player.hasEffect(ModEffects.TIME_SLOW);
        if (!stopped && !slowed) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (stopped) {
            drawFrozen(graphics, width, height);
        } else {
            drawDilation(graphics, width, height);
        }
        drawStatus(graphics, minecraft.font, width, height, stopped);
    }

    /** 变慢：淡蓝滤镜 + 横向拖影 + 移动扫描线 + 暗角 */
    private static void drawDilation(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0x2A2F6BFF);

        long now = System.currentTimeMillis();
        Random random = new Random(now / 45L);
        for (int i = 0; i < 10; i++) {
            int y = random.nextInt(height);
            int length = 24 + random.nextInt(110);
            int x = random.nextInt(width + 160) - 160;
            int alpha = 0x40 + random.nextInt(0x50);
            graphics.fill(x, y, x + length, y + 1, (alpha << 24) | 0x9FE8FF);
        }

        // 间距 8 像素已足够形成扫描线观感，同时把每帧的绘制次数减半
        int offset = (int) (now / 30L % 8L);
        for (int y = -8 + offset; y < height; y += 8) {
            graphics.fill(0, y, width, y + 1, 0x14AEEBFF);
        }

        drawVignette(graphics, width, height, 0x55224C9C);
    }

    /** 暂停：冷色蒙版 + 静止暗角，像整个画面被冻住 */
    private static void drawFrozen(GuiGraphics graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0x38D8F2FF);
        // 一道横贯画面的冻结光带，强调时间已经停住
        int band = height / 3;
        graphics.fillGradient(0, band, width, band + height / 6, 0x1AFFFFFF, 0x00000000);
        drawVignette(graphics, width, height, 0x66A8C8DE);
    }

    /** 四周暗角，让滤镜更有镜头感 */
    private static void drawVignette(GuiGraphics graphics, int width, int height, int edgeColor) {
        int band = Math.max(24, Math.min(width, height) / 7);
        int sideColor = (edgeColor & 0x00FFFFFF) | (((edgeColor >>> 24) / 2) << 24);
        graphics.fillGradient(0, 0, width, band, edgeColor, 0x00000000);
        graphics.fillGradient(0, height - band, width, height, 0x00000000, edgeColor);
        graphics.fill(0, 0, band, height, sideColor);
        graphics.fill(width - band, 0, width, height, sideColor);
    }

    /** 快捷栏上方的状态条：模式名 + 简要说明 */
    private static void drawStatus(GuiGraphics graphics, Font font, int width, int height, boolean stopped) {
        Component label = Component.translatable(stopped
                ? "endbound_ember.time_halo.mode_stop"
                : "endbound_ember.time_halo.mode_slow");
        Component detail = stopped
                ? Component.translatable("endbound_ember.time_halo.detail_stop")
                : Component.translatable("endbound_ember.time_halo.detail_slow", slowPercent());
        int accent = stopped ? 0xFFDDF3FF : 0xFF8FE8FF;

        int boxWidth = Math.max(font.width(label), font.width(detail)) + 22;
        int left = (width - boxWidth) / 2;
        int top = height - 70;
        graphics.fill(left, top, left + boxWidth, top + 26, 0xB00B1526);
        graphics.fill(left, top, left + 3, top + 26, accent);
        graphics.drawString(font, label, left + 13, top + 4, accent, false);
        graphics.drawString(font, detail, left + 13, top + 14, 0xFFB9C6D8, false);
    }

    /** 时间变慢的流速百分比，用于状态条说明 */
    private static String slowPercent() {
        double factor = Config.TIME_HALO_SLOW_FACTOR.get();
        return String.valueOf(Math.round(factor * 100.0D));
    }
}
