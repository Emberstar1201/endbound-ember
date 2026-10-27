package com.emberstar.endbounember.client;

import com.emberstar.endbounember.entity.HumanGirl;
import com.emberstar.endbounember.entity.ZombieGirl;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 文本对话界面。回复写入客户端原版聊天栏并保存到本地记忆，不触发 TTS。 */
public final class AiDialogueScreen extends Screen {
    private final Screen parent;
    private final int entityId;
    private EditBox input;
    private boolean waiting;

    public AiDialogueScreen(Screen parent, int entityId) {
        super(Component.translatable("gui.endbound_ember.ai.dialogue"));
        this.parent = parent;
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        input = new EditBox(font, 20, height - 62, width - 140, 20, Component.empty());
        input.setMaxLength(1000);
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.ai.send"), button -> send())
                .bounds(width - 110, height - 62, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.back"), button -> minecraft.setScreen(parent))
                .bounds(width / 2 - 50, height - 30, 100, 20).build());
    }

    private void send() {
        if (waiting) return;
        if (input.getValue().isBlank()) return;
        ZombieGirl girl = minecraft.level != null && minecraft.level.getEntity(entityId) instanceof ZombieGirl value ? value : null;
        if (girl == null) {
            // 目标已不在加载范围内：必须给出提示，否则玩家只会看到点击后毫无反应。
            ChatFeedback.error(Component.translatable("gui.endbound_ember.ai.target_lost"));
            return;
        }
        String message = input.getValue().trim();
        var config = AiConfigurationStore.get();
        String key = girl instanceof HumanGirl ? "survivor_girl"
                : girl.getType().toString().contains("drowned") ? "drowned_girl" : "zombie_girl";
        var girlId = girl.getUUID();
        var persona = AiConfigurationStore.persona(girlId, key);
        String background = AiConfigurationStore.backgroundFor(girl.diaryVariant());
        String girlName = girl.hasCustomName() ? girl.getCustomName().getString() : key;
        waiting = true;
        input.setValue("");
        // 回复统一走 ChatFeedback：即使玩家在等待期间关闭了本界面，结果仍会写进聊天栏。
        ChatFeedback.waiting(girlName);
        AiServiceClient.generate(config.text, persona,
                girlName, girl.getAffection(), background, AiConfigurationStore.memory(girlId), message)
                .thenAccept(result -> minecraft.execute(() -> {
                    waiting = false;
                    if (result.isBlank()) {
                        ChatFeedback.error(Component.translatable("gui.endbound_ember.ai.failed"));
                        return;
                    }
                    AiConfigurationStore.add(girlId, message, result);
                    ChatFeedback.reply(girlName, result);
                }));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        if (waiting) graphics.drawCenteredString(font, Component.translatable("gui.endbound_ember.ai.waiting"), width / 2, height / 2, 0xCCCCCC);
        super.render(graphics, mouseX, mouseY, partialTick);
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

    @Override
    public void removed() {
        // 释放输入焦点：避免关闭界面后输入法仍绑定在已消失的 EditBox 上，导致游戏中按键失效。
        setFocused(null);
        super.removed();
    }
}
