package com.emberstar.endbounember.client;

import com.emberstar.endbounember.entity.ZombieGirl;
import com.emberstar.endbounember.entity.HumanGirl;
import com.emberstar.endbounember.network.GirlInteractionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 少女互动面板。保留旧版的折叠菜单、对话、事件簿、配置和跟随控制。 */
public final class GirlInteractionScreen extends Screen {
    private final int entityId;
    private boolean expanded;
    private EditBox nameBox;

    public GirlInteractionScreen(int entityId) {
        super(Component.translatable("gui.endbound_ember.girl.title"));
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = Math.max(8, width - 178);
        int y = height / 2 - (expanded ? 104 : 76);
        addRenderableWidget(Button.builder(Component.literal(expanded ? "-" : "+"), button -> {
            expanded = !expanded;
            rebuildWidgets();
        }).bounds(x - 24, y, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.girl.dialogue"),
                button -> minecraft.setScreen(new AiDialogueScreen(this, entityId))).bounds(x, y, 160, 20).build());
        y += 24;
        if (expanded) {
            addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.girl.events"),
                    button -> minecraft.setScreen(new AiMemoryScreen(this, entityId))).bounds(x + 10, y, 150, 20).build());
            y += 24;
            addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.girl.persona"),
                    button -> minecraft.setScreen(new AiConfigurationScreen(this, personaKey()))).bounds(x + 10, y, 150, 20).build());
            y += 24;
            nameBox = new EditBox(font, x + 10, y, 150, 20, Component.translatable("gui.endbound_ember.girl.name"));
            nameBox.setMaxLength(32);
            addRenderableWidget(nameBox);
            addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.girl.rename"), button -> rename())
                    .bounds(x - 24, y, 20, 20).build());
            y += 24;
        }
        ZombieGirl girl = currentGirl();
        boolean sitting = girl != null && girl.isOrderedToSit();
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.girl.follow"),
                button -> send(GirlInteractionPayload.FOLLOW)).bounds(x, y, 160, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.girl.stay"),
                button -> send(GirlInteractionPayload.STAY)).bounds(x, y, 160, 20).build());
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable(sitting
                        ? "gui.endbound_ember.girl.state.staying" : "gui.endbound_ember.girl.state.following"),
                button -> {}).bounds(x, y, 160, 20).build()).active = false;
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("gui.endbound_ember.back"), button -> onClose())
                .bounds(x, y, 160, 20).build());
    }

    private ZombieGirl currentGirl() {
        if (minecraft == null || minecraft.level == null) return null;
        return minecraft.level.getEntity(entityId) instanceof ZombieGirl girl ? girl : null;
    }

    private String personaKey() {
        ZombieGirl girl = currentGirl();
        if (girl == null) return "zombie_girl";
        if (girl instanceof HumanGirl) return "survivor_girl";
        return girl.getType().toString().contains("drowned") ? "drowned_girl" : "zombie_girl";
    }

    private void send(int action) {
        PacketDistributor.sendToServer(new GirlInteractionPayload(entityId, action, ""));
        rebuildWidgets();
    }

    private void rename() {
        if (nameBox == null || nameBox.getValue().isBlank()) return;
        PacketDistributor.sendToServer(new GirlInteractionPayload(entityId, GirlInteractionPayload.RENAME, nameBox.getValue()));
        nameBox.setValue("");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = Math.max(8, width - 178);
        int y = height / 2 - (expanded ? 128 : 100);
        graphics.drawString(font, title, x, y, 0xFFFFFF);
        ZombieGirl girl = currentGirl();
        if (girl != null) {
            graphics.drawString(font, Component.translatable("gui.endbound_ember.affection", girl.getAffection()), x, y + 14, 0xFFFFFF);
            if (girl.hasCustomName()) graphics.drawString(font, girl.getCustomName(), x, y + 28, 0xD0D0D0);
        }
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
}
