package com.emberstar.endbounember.client;

import java.util.ArrayList;
import java.util.Collection;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;

/**
 * 在配置界面的「游戏配置」分区末尾追加「BOSS 战配乐」入口。
 * <p>点开后会直接打开系统资源管理器，定位到玩家应把 OGG 放进去的目录。</p>
 */
public class BossMusicConfigScreen extends ConfigurationSectionScreen {
    /** 本分区对应的配置文件，用来判断是否要显示配乐入口 */
    private final ModConfig modConfig;

    public BossMusicConfigScreen(Screen parent, ModConfig.Type type, ModConfig modConfig, Component title) {
        super(parent, type, modConfig, title);
        this.modConfig = modConfig;
    }

    @Override
    protected Collection<? extends Element> createSyntheticValues() {
        Collection<Element> elements = new ArrayList<>(super.createSyntheticValues());
        // 只有游戏配置分区需要这个入口
        if (modConfig.getType() != ModConfig.Type.COMMON) return elements;

        Button folderButton = Button.builder(
                Component.translatable("endbound_ember.configuration.bossMusic.openFolder"),
                pressed -> BossMusicPack.openImportFolder())
                .width(Button.DEFAULT_WIDTH)
                .build();
        elements.add(new Element(
                Component.translatable("endbound_ember.configuration.bossMusic.import"),
                Component.translatable("endbound_ember.configuration.bossMusic.import.tooltip"),
                folderButton,
                false));

        Button playlistButton = Button.builder(
                Component.translatable("endbound_ember.configuration.bossMusic.playlist.button"),
                pressed -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new BossMusicPlaylistScreen(this));
                    }
                })
                .width(Button.DEFAULT_WIDTH)
                .build();
        elements.add(new Element(
                Component.translatable("endbound_ember.configuration.bossMusic.playlist"),
                Component.translatable("endbound_ember.configuration.bossMusic.playlist.tooltip"),
                playlistButton,
                false));
        return elements;
    }
}
