package com.emberstar.endbounember.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MusicPlaylistScreen extends Screen {
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 48;
    private static final int LIST_BOTTOM = 180;
    private final Screen parent;
    private int scroll;
    private int category;

    public MusicPlaylistScreen(Screen parent) {
        super(Component.translatable("endbound_ember.music_player.title"));
        this.parent = parent;
    }

    private void rebuild() {
        clearWidgets();
        ClientMusicPlayer.refresh();
        int margin = Math.min(10, Math.max(4, width / 40));
        int gap = 4;
        int tabWidth = Math.max(44, (width - margin * 2 - gap * 4) / 5);
        int left = (width - (tabWidth * 5 + gap * 4)) / 2;
        String[] tabs = {"all", "menu", "creative", "world", "disc"};
        for (int i = 0; i < tabs.length; i++) {
            final int tab = i;
            addRenderableWidget(Button.builder(Component.translatable("endbound_ember.music_player.category." + tabs[i]), b -> {
                category = tab;
                scroll = 0;
                rebuild();
            }).bounds(left + i * (tabWidth + gap), 18, tabWidth, 20).build());
        }
        int controlsY = height - 67;
        int controlWidth = Math.min(380, width - margin * 2);
        int x = (width - controlWidth) / 2;
        int smallWidth = Math.max(42, (controlWidth - 16) / 4);
        int modeWidth = controlWidth - smallWidth * 3 - 16;
        addRenderableWidget(Button.builder(Component.translatable("endbound_ember.music_player.previous"), b -> {
            ClientMusicPlayer.previous();
            rebuild();
        }).bounds(x, controlsY, smallWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(ClientMusicPlayer.isPlaying()
                        ? "endbound_ember.music_player.pause" : "endbound_ember.music_player.play"), b -> {
            ClientMusicPlayer.toggle();
            rebuild();
        }).bounds(x + smallWidth + 4, controlsY, smallWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("endbound_ember.music_player.next"), b -> {
            ClientMusicPlayer.next();
            rebuild();
        }).bounds(x + (smallWidth + 4) * 2, controlsY, smallWidth, 20).build());
        addRenderableWidget(Button.builder(modeLabel(), b -> {
            ClientMusicPlayer.cycleMode();
            rebuild();
        }).bounds(x + (smallWidth + 4) * 3, controlsY, modeWidth, 20).build());
        int actionGap = 6;
        int actionWidth = (controlWidth - actionGap * 2) / 3;
        addRenderableWidget(Button.builder(Component.translatable("endbound_ember.music_player.import"), b -> ClientMusicPack.openFolder())
                .bounds(x, controlsY + 25, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("endbound_ember.music_player.refresh"), b -> {
            ClientMusicPlayer.refresh();
            rebuild();
        }).bounds(x + actionWidth + actionGap, controlsY + 25, actionWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(x + (actionWidth + actionGap) * 2, controlsY + 25, actionWidth, 20).build());
    }

    private Component modeLabel() {
        String key = switch (ClientMusicPlayer.mode()) {
            case SINGLE -> "endbound_ember.music_player.mode.single";
            case LIST -> "endbound_ember.music_player.mode.list";
            case NONE -> "endbound_ember.music_player.mode.none";
        };
        return Component.translatable("endbound_ember.music_player.mode", Component.translatable(key));
    }

    private boolean visible(int index) {
        return ClientMusicPlayer.isCategory(index, category);
    }

    private int visibleCount() {
        int count = 0;
        for (int i = 0; i < ClientMusicPlayer.size(); i++) if (visible(i)) count++;
        return count;
    }

    @Override
    protected void init() {
        rebuild();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Screen.renderMenuBackgroundTexture(graphics,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/block/dirt.png"),
                0, 0, 0.0F, 0.0F, width, height);
        graphics.fill(0, 0, width, height, 0x99000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int listLeft = Math.max(8, width / 2 - 245);
        int listRight = Math.min(width - 8, width / 2 + 245);
        int listBottom = Math.min(height - 91, LIST_BOTTOM + Math.max(0, height - 240));
        graphics.fill(listLeft, LIST_TOP, listRight, listBottom, 0x66000000);
        int row = 0;
        int visibleIndex = 0;
        for (int i = 0; i < ClientMusicPlayer.size(); i++) {
            if (!visible(i)) continue;
            if (visibleIndex++ < scroll) continue;
            int y = LIST_TOP + row * ROW_HEIGHT;
            if (y + ROW_HEIGHT > listBottom) break;
            boolean selected = i == ClientMusicPlayer.currentIndex();
            if (selected) graphics.fill(listLeft + 4, y, listRight - 4, y + ROW_HEIGHT - 1, 0x66505050);
            if (mouseX >= listLeft && mouseX <= listRight && mouseY >= y && mouseY < y + ROW_HEIGHT && !selected) {
                graphics.fill(listLeft + 4, y, listRight - 4, y + ROW_HEIGHT - 1, 0x33FFFFFF);
            }
            String name = ClientMusicPlayer.trackName(i);
            int detailWidth = font.width(trackDetail(i));
            int nameMaxWidth = Math.max(0, listRight - listLeft - detailWidth - 32);
            if (font.width(name) > nameMaxWidth) name = font.plainSubstrByWidth(name, Math.max(0, nameMaxWidth - font.width("…"))) + "…";
            graphics.drawString(font, name, listLeft + 10, y + 8, 0xFFFFFFFF);
            graphics.drawString(font, trackDetail(i), listRight - 10 - detailWidth, y + 8, 0xFFB0B0B0);
            row++;
        }
        String current = ClientMusicPlayer.currentName();
        graphics.drawCenteredString(font, current.isEmpty()
                ? Component.translatable("endbound_ember.music_player.empty") : Component.literal(current),
                width / 2, height - 82, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("endbound_ember.music_player.reload_hint"),
                width / 2, height - 16, 0xFFA0A0A0);
    }

    private String trackDetail(int index) {
        String name = ClientMusicPlayer.trackName(index);
        if (ClientMusicPlayer.isVanilla(index)) return Component.translatable("endbound_ember.music_player.vanilla").getString();
        return Component.translatable("endbound_ember.music_player.imported").getString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listLeft = Math.max(8, width / 2 - 245);
        int listRight = Math.min(width - 8, width / 2 + 245);
        int listBottom = Math.min(height - 91, LIST_BOTTOM + Math.max(0, height - 240));
        if (button == 0 && mouseX >= listLeft && mouseX <= listRight && mouseY >= LIST_TOP && mouseY < listBottom) {
            int targetRow = (int) (mouseY - LIST_TOP) / ROW_HEIGHT;
            int row = 0;
            int visibleIndex = 0;
            for (int i = 0; i < ClientMusicPlayer.size(); i++) {
                if (!visible(i)) continue;
                if (visibleIndex++ < scroll) continue;
                if (row++ == targetRow) {
                    ClientMusicPlayer.select(i);
                    rebuild();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY < LIST_TOP || mouseY > Math.min(height - 91, LIST_BOTTOM + Math.max(0, height - 240))) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int rows = Math.max(1, (Math.min(height - 91, LIST_BOTTOM + Math.max(0, height - 240)) - LIST_TOP) / ROW_HEIGHT);
        scroll = Math.max(0, Math.min(scroll - (int) Math.signum(scrollY) * 3, Math.max(0, visibleCount() - rows)));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
