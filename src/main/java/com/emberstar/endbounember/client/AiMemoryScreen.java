package com.emberstar.endbounember.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 展示当前少女的本地事件记录和最近对话。 */
public final class AiMemoryScreen extends Screen {
    private final Screen parent;
    private final java.util.UUID girlId;

    public AiMemoryScreen(Screen parent, int entityId) {
        super(Component.translatable("gui.endbound_ember.events"));
        this.parent = parent;
        var client = Minecraft.getInstance();
        var entity = client.level == null ? null : client.level.getEntity(entityId);
        this.girlId = entity == null ? new java.util.UUID(0L, 0L) : entity.getUUID();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.back"), button -> minecraft.setScreen(parent))
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        var memory = AiConfigurationStore.memory(girlId);
        int y = 42;
        if (memory.entries.isEmpty() && memory.history.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.endbound_ember.events.empty"), width / 2, y, 0xCCCCCC);
        } else {
            for (String entry : memory.entries) {
                y = drawWrapped(graphics, entry, y, 0xD0D0D0);
            }
            for (var message : memory.history) {
                if (message == null || message.content == null) continue;
                y = drawWrapped(graphics, ("user".equals(message.role) ? "玩家：" : "少女：") + message.content, y, 0xFFFFFF);
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private int drawWrapped(GuiGraphics graphics, String text, int y, int color) {
        for (var line : font.split(Component.literal(text), Math.max(120, width - 40))) {
            graphics.drawString(font, line, 20, y, color);
            y += 10;
            if (y > height - 42) return y;
        }
        return y + 4;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1.21 默认背景走模糊后期管线，会把裸绘文字一并染糊；改用旧版的半透明暗色遮罩。
        if (minecraft != null && minecraft.level != null) {
            graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        } else {
            super.renderBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
