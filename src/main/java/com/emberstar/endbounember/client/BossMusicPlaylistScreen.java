package com.emberstar.endbounember.client;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.emberstar.endbounember.entity.HerobrineBossMusic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * BOSS 战配乐歌单：按文件名 A-Z 列出导入目录里的所有 OGG，点击即可选用。
 * <p>选择结果会立即写入配置文件，并按 F3+T 重载资源后生效。</p>
 */
public class BossMusicPlaylistScreen extends Screen {
    /** 每页显示的行数 */
    private static final int ROWS_PER_PAGE = 6;

    private final Screen parent;
    private int page;
    private Component status = Component.empty();

    public BossMusicPlaylistScreen(Screen parent) {
        super(Component.translatable("endbound_ember.configuration.bossMusic.playlist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        List<Path> tracks = BossMusicPack.playlist();
        int pages = Math.max(1, (tracks.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        this.page = Math.min(this.page, pages - 1);

        int left = this.width / 2 - 155;
        int top = 62;
        int rowHeight = 24;

        addRenderableWidget(Button.builder(
                label(Component.translatable("endbound_ember.configuration.bossMusic.playlist.default"),
                        HerobrineBossMusic.selectedTrack().isEmpty() && tracks.isEmpty()),
                pressed -> select(null))
                .bounds(left, top, 310, 20)
                .build());

        int start = this.page * ROWS_PER_PAGE;
        for (int i = 0; i < ROWS_PER_PAGE && start + i < tracks.size(); i++) {
            Path track = tracks.get(start + i);
            String name = track.getFileName().toString();
            addRenderableWidget(Button.builder(
                    label(Component.literal(name.length() > 40 ? name.substring(0, 39) + "…" : name),
                            isSelected(track)),
                    pressed -> select(track))
                    .bounds(left, top + rowHeight * (i + 1), 310, 20)
                    .build());
        }

        int navY = top + rowHeight * (ROWS_PER_PAGE + 1) + 12;
        if (pages > 1) {
            addRenderableWidget(Button.builder(
                    Component.translatable("endbound_ember.configuration.bossMusic.playlist.prev"),
                    pressed -> {
                        this.page = (this.page + pages - 1) % pages;
                        this.init();
                    })
                    .bounds(left, navY, 100, 20)
                    .build());
            addRenderableWidget(Button.builder(
                    Component.translatable("endbound_ember.configuration.bossMusic.playlist.next"),
                    pressed -> {
                        this.page = (this.page + 1) % pages;
                        this.init();
                    })
                    .bounds(left + 210, navY, 100, 20)
                    .build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, pressed -> onClose())
                .bounds(left + 105, navY, 100, 20)
                .build());
    }

    /** 选中状态在按钮文字前加一个标记 */
    private Component label(Component text, boolean selected) {
        if (!selected) return text;
        return Component.translatable("endbound_ember.configuration.bossMusic.playlist.selected")
                .append(" ")
                .append(text);
    }

    /** 没点选过时，实际生效的是 A-Z 排序的第一首 */
    private boolean isSelected(Path track) {
        Optional<Path> selected = HerobrineBossMusic.selectedTrack();
        Path target = track.toAbsolutePath().normalize();
        if (selected.isPresent()) {
            return selected.get().equals(target);
        }
        List<Path> tracks = BossMusicPack.playlist();
        return !tracks.isEmpty() && tracks.get(0).toAbsolutePath().normalize().equals(target);
    }

    private void select(Path track) {
        HerobrineBossMusic.selectTrack(track);
        BossMusicPack.syncActiveTrack();
        this.status = Component.translatable("endbound_ember.configuration.bossMusic.playlist.applied");
        this.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 24, 0xFFFFFF);
        if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, 40, 0xFFFF80);
        } else if (BossMusicPack.playlist().isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("endbound_ember.configuration.bossMusic.playlist.empty"),
                    this.width / 2, 72, 0xA0A0A0);
        }
        graphics.drawCenteredString(this.font,
                Component.translatable("endbound_ember.configuration.bossMusic.playlist.hint"),
                this.width / 2, this.height - 36, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
