package com.emberstar.endbounember.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.HerobrineBossMusic;

import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * 把玩家导入目录注册成常驻客户端资源包。
 * <p>Minecraft 客户端只从资源包加载音频，所以导入的 OGG 必须通过资源包才能生效。
 * 每次重载资源前会把歌单里生效的那首曲子复制成固定文件名，再注册这个
 * 「始终启用、不可关闭」的资源包，位置在最上层，用来覆盖 mod 自带的默认曲目。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class BossMusicPack {
    private static final String PACK_ID = "endbound_ember_boss_music";

    /** 1.21.1 的资源包版本为 34；同时声明一个宽泛的兼容区间，避免版本校验把包判成不兼容 */
    private static final String PACK_META = """
            {
              "pack": {
                "description": "Endbound Ember - Herobrine boss music",
                "pack_format": 34,
                "supported_formats": [15, 9999]
              }
            }
            """;

    private BossMusicPack() {
    }

    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;

        ensurePack();
        syncActiveTrack();
        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo(PACK_ID,
                        Component.translatable("pack.endbound_ember.boss_music"),
                        PackSource.BUILT_IN, Optional.empty()),
                new PathPackResources.PathResourcesSupplier(HerobrineBossMusic.generatedPackRoot()),
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, true));
        if (pack == null) {
            EndboundEmber.LOGGER.warn("[BOSS 战配乐] 生成的资源包缺少 pack.mcmeta，本次不加载导入曲目");
            return;
        }
        event.addRepositorySource(consumer -> consumer.accept(pack));

        Optional<Path> active = HerobrineBossMusic.activeTrack();
        EndboundEmber.LOGGER.info("[BOSS 战配乐] 导入资源包已注册: 曲目={}, 共 {} 首可选",
                active.map(path -> path.getFileName().toString()).orElse("未导入，使用自带默认曲目"),
                HerobrineBossMusic.importedOggSources().size());
    }

    /** 打开系统资源管理器并定位到玩家目录，任意位置的 OGG 都会被歌单收录 */
    public static Path openImportFolder() {
        Path root = ensurePack();
        Util.getPlatform().openFile(root.toFile());
        return root;
    }

    /**
     * 确保目录骨架存在：玩家目录、生成的资源包目录、pack.mcmeta 与音效目录。
     * <p>玩家提前（游戏启动前）手动放入文件时也会在首次加载资源时被识别。</p>
     */
    public static Path ensurePack() {
        Path root = HerobrineBossMusic.importedPackRoot();
        Path packRoot = HerobrineBossMusic.generatedPackRoot();
        try {
            Files.createDirectories(HerobrineBossMusic.importedMusicDir());
            Path meta = packRoot.resolve("pack.mcmeta");
            if (!Files.isRegularFile(meta)) {
                Files.writeString(meta, PACK_META, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            EndboundEmber.LOGGER.warn("[BOSS 战配乐] 创建导入目录失败: {}", packRoot, e);
        }
        return root;
    }

    /**
     * 把歌单里生效的曲目复制成资源包引用的固定文件名。
     * <p>没有可选曲目时清掉旧副本，让客户端重新使用 mod 自带的默认曲目。</p>
     */
    public static void syncActiveTrack() {
        Path served = HerobrineBossMusic.importedMusicFile();
        Path source = HerobrineBossMusic.activeTrack().orElse(null);

        if (source == null) {
            try {
                if (Files.deleteIfExists(served)) {
                    EndboundEmber.LOGGER.info("[BOSS 战配乐] 没有导入曲目，已清除旧副本");
                }
            } catch (IOException e) {
                EndboundEmber.LOGGER.warn("[BOSS 战配乐] 清除旧副本失败: {}", served, e);
            }
            return;
        }

        try {
            Files.createDirectories(served.getParent());
            // 曲目文件通常只有几 MB，每次重载直接覆盖，避免同大小文件被误判为已同步
            Files.copy(source, served, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            EndboundEmber.LOGGER.warn("[BOSS 战配乐] 复制曲目失败: {} -> {}", source, served, e);
        }
    }

    /** 歌单里可选择的所有曲目，按文件名 A-Z 排序 */
    public static List<Path> playlist() {
        return HerobrineBossMusic.importedOggSources();
    }
}
