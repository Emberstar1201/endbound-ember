package com.emberstar.endbounember.client;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.DualWieldCombat;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class DualWieldCooldownHud {
    private static final ResourceLocation CROSSHAIR_BACKGROUND = ResourceLocation.withDefaultNamespace(
            "hud/crosshair_attack_indicator_background");
    private static final ResourceLocation CROSSHAIR_PROGRESS = ResourceLocation.withDefaultNamespace(
            "hud/crosshair_attack_indicator_progress");
    private static final ResourceLocation HOTBAR_BACKGROUND = ResourceLocation.withDefaultNamespace(
            "hud/hotbar_attack_indicator_background");
    private static final ResourceLocation HOTBAR_PROGRESS = ResourceLocation.withDefaultNamespace(
            "hud/hotbar_attack_indicator_progress");

    private DualWieldCooldownHud() {
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null
                || !Config.DUAL_WIELD_ENABLED.get()
                || !DualWieldCombat.hasMatchingDualWeapon(minecraft.player)) return;

        AttackIndicatorStatus status = minecraft.options.attackIndicator().get();
        if (status == AttackIndicatorStatus.OFF) return;

        if (!DualWieldCooldownClient.isCoolingDown()) return;

        float progress = DualWieldCooldownClient.progress();
        GuiGraphics graphics = event.getGuiGraphics();
        if (status == AttackIndicatorStatus.CROSSHAIR) {
            renderCrosshairIndicator(graphics, progress);
        } else if (status == AttackIndicatorStatus.HOTBAR) {
            renderHotbarIndicator(minecraft, graphics, progress);
        }
    }

    private static void renderCrosshairIndicator(GuiGraphics graphics, float progress) {
        int x = (graphics.guiWidth() - 16) / 2;
        int y = graphics.guiHeight() / 2 - 7 + 16 + 5;
        int fill = Math.round(16 * progress);
        graphics.blitSprite(CROSSHAIR_BACKGROUND, x, y, 16, 4);
        if (fill > 0) {
            // 进度从左向右增长，目标位置与背景左边缘对齐。
            graphics.blitSprite(CROSSHAIR_PROGRESS, 16, 4, 0, 0, x, y, fill, 4);
        }
    }

    private static void renderHotbarIndicator(Minecraft minecraft, GuiGraphics graphics, float progress) {
        int centerX = graphics.guiWidth() / 2;
        HumanoidArm offhandArm = minecraft.player.getMainArm().getOpposite();
        // 将图标放在原版副手槽装饰框外侧，避免遮挡副手物品。
        int x = offhandArm == HumanoidArm.LEFT ? centerX - 91 - 29 - 36 : centerX + 91 + 29;
        int y = graphics.guiHeight() - 20;
        int fill = Math.round(18 * progress);

        graphics.blitSprite(HOTBAR_BACKGROUND, x, y, 18, 18);
        if (fill > 0) {
            // 保持完整宽度，仅从底部按冷却进度向上填充。
            graphics.blitSprite(HOTBAR_PROGRESS, 18, 18, 0, 18 - fill, x, y + 18 - fill, 18, fill);
        }
    }
}
