package com.emberstar.endbounember.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import com.emberstar.endbounember.EndboundEmber;

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
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class ClientMusicPack {
    private static final String NAMESPACE = "endbound_ember_player";
    private static final Path ROOT = FMLPaths.CONFIGDIR.get().resolve("endbound_ember").resolve("music");
    private static final Path PACK = ROOT.resolve(".generated_pack");

    private ClientMusicPack() {
    }

    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        refreshPack();
        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo("endbound_ember_client_music",
                        Component.translatable("endbound_ember.music_player.title"), PackSource.BUILT_IN, Optional.empty()),
                new PathPackResources.PathResourcesSupplier(PACK), PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, true));
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    public static Path openFolder() {
        refreshPack();
        Util.getPlatform().openFile(ROOT.toFile());
        return ROOT;
    }

    public static List<Path> importedTracks() {
        try (Stream<Path> paths = Files.walk(ROOT)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> !path.startsWith(PACK))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted((a, b) -> a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static boolean refreshPack() {
        List<Path> tracks;
        try {
            Files.createDirectories(ROOT);
            Files.createDirectories(PACK.resolve("assets").resolve(NAMESPACE).resolve("sounds").resolve("music"));
            Files.writeString(PACK.resolve("pack.mcmeta"),
                    "{\"pack\":{\"description\":\"Endbound Ember client music\",\"pack_format\":34,\"supported_formats\":[15,9999]}}",
                    StandardCharsets.UTF_8);
            tracks = importedTracks();
            StringBuilder sounds = new StringBuilder("{\n");
            for (int i = 0; i < tracks.size(); i++) {
                String name = soundName(i);
                Path target = PACK.resolve("assets").resolve(NAMESPACE).resolve("sounds").resolve("music")
                        .resolve(name + ".ogg");
                Files.copy(tracks.get(i), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                // 声音文件名必须显式带命名空间，否则声音系统会默认到 minecraft 下查找。
                sounds.append("  \"").append(name).append("\":{\"sounds\":[{\"name\":\"")
                        .append(NAMESPACE).append(":music/").append(name).append("\",\"stream\":true}]}");
                sounds.append(i + 1 == tracks.size() ? "\n" : ",\n");
            }
            sounds.append("}\n");
            try (Stream<Path> generated = Files.list(PACK.resolve("assets").resolve(NAMESPACE).resolve("sounds").resolve("music"))) {
                List<Path> files = generated.toList();
                for (Path file : files) {
                    if (!file.getFileName().toString().endsWith(".ogg")) continue;
                    String expected = tracks.stream().anyMatch(track -> soundName(tracks.indexOf(track))
                            .concat(".ogg").equals(file.getFileName().toString())) ? file.getFileName().toString() : null;
                    if (expected == null) Files.deleteIfExists(file);
                }
            }
            Files.writeString(PACK.resolve("assets").resolve(NAMESPACE).resolve("sounds.json"), sounds,
                    StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            EndboundEmber.LOGGER.warn("[音乐播放器] 更新客户端音乐资源包失败", e);
            return false;
        }
    }

    static String soundName(int index) {
        return "imported_" + index;
    }

    public static ResourceTrack importedTrack(int index) {
        List<Path> tracks = importedTracks();
        if (index < 0 || index >= tracks.size()) return null;
        return new ResourceTrack(tracks.get(index).getFileName().toString(),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(NAMESPACE, soundName(index)));
    }

    public record ResourceTrack(String name, net.minecraft.resources.ResourceLocation id) {
    }
}
