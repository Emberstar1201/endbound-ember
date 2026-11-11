package com.emberstar.endbounember.client;

import com.emberstar.endbounember.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "endbound_ember", value = Dist.CLIENT)
public final class EquipmentDurabilityHud {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND
    };
    private static final String[] LABELS = {
            "hud.endbound_ember.durability.helmet",
            "hud.endbound_ember.durability.chestplate",
            "hud.endbound_ember.durability.leggings",
            "hud.endbound_ember.durability.boots",
            "hud.endbound_ember.durability.mainhand",
            "hud.endbound_ember.durability.offhand"
    };

    private EquipmentDurabilityHud() {
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!Config.DURABILITY_HUD_ENABLED.get()
                || minecraft.player == null
                || minecraft.options.hideGui
                || minecraft.player.isSpectator()
                || minecraft.screen != null) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int lineHeight = 10;
        int totalHeight = SLOTS.length * lineHeight;
        int x = Config.DURABILITY_HUD_X_OFFSET.get();
        int y = minecraft.getWindow().getGuiScaledHeight() / 2
                + Config.DURABILITY_HUD_Y_OFFSET.get() - totalHeight / 2;

        for (int index = 0; index < SLOTS.length; index++) {
            ItemStack stack = minecraft.player.getItemBySlot(SLOTS[index]);
            String label = net.minecraft.network.chat.Component.translatable(LABELS[index]).getString();
            String value = stack.isDamageableItem()
                    ? label + ": " + (stack.getMaxDamage() - stack.getDamageValue())
                    : label;
            int color = stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue()
                    <= Math.max(1, stack.getMaxDamage() / 5) ? 0xFFFF5555 : 0xFFFFFFFF;
            graphics.drawString(minecraft.font, value, x, y + index * lineHeight, color, true);
        }
    }
}
