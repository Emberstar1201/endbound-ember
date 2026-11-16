package com.emberstar.endbounember.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public final class ClientMusicPlayer {
    public enum Mode { SINGLE, LIST, NONE }
    public enum Category { MENU, CREATIVE, WORLD, DISC }

    private record Track(String name, ResourceLocation id, Category category) {}

    private static Track vanilla(String name, String id, Category category) {
        return new Track(name, ResourceLocation.withDefaultNamespace(id), category);
    }

    private static final List<Track> VANILLA = List.of(
            vanilla("Minecraft - Menu", "music.menu", Category.MENU),
            vanilla("Minecraft - Creative", "music.creative", Category.CREATIVE),
            vanilla("Minecraft - Game", "music.game", Category.WORLD),
            vanilla("Minecraft - Credits", "music.credits", Category.WORLD),
            vanilla("Minecraft - End", "music.end", Category.WORLD),
            vanilla("Minecraft - Dragon", "music.dragon", Category.WORLD),
            vanilla("Minecraft - Under Water", "music.under_water", Category.WORLD),
            vanilla("Minecraft - Basalt Deltas", "music.nether.basalt_deltas", Category.WORLD),
            vanilla("Minecraft - Crimson Forest", "music.nether.crimson_forest", Category.WORLD),
            vanilla("Minecraft - Nether Wastes", "music.nether.nether_wastes", Category.WORLD),
            vanilla("Minecraft - Soul Sand Valley", "music.nether.soul_sand_valley", Category.WORLD),
            vanilla("Minecraft - Warped Forest", "music.nether.warped_forest", Category.WORLD),
            vanilla("Minecraft - Badlands", "music.overworld.badlands", Category.WORLD),
            vanilla("Minecraft - Bamboo Jungle", "music.overworld.bamboo_jungle", Category.WORLD),
            vanilla("Minecraft - Cherry Grove", "music.overworld.cherry_grove", Category.WORLD),
            vanilla("Minecraft - Deep Dark", "music.overworld.deep_dark", Category.WORLD),
            vanilla("Minecraft - Desert", "music.overworld.desert", Category.WORLD),
            vanilla("Minecraft - Dripstone Caves", "music.overworld.dripstone_caves", Category.WORLD),
            vanilla("Minecraft - Flower Forest", "music.overworld.flower_forest", Category.WORLD),
            vanilla("Minecraft - Forest", "music.overworld.forest", Category.WORLD),
            vanilla("Minecraft - Frozen Peaks", "music.overworld.frozen_peaks", Category.WORLD),
            vanilla("Minecraft - Grove", "music.overworld.grove", Category.WORLD),
            vanilla("Minecraft - Jagged Peaks", "music.overworld.jagged_peaks", Category.WORLD),
            vanilla("Minecraft - Jungle", "music.overworld.jungle", Category.WORLD),
            vanilla("Minecraft - Lush Caves", "music.overworld.lush_caves", Category.WORLD),
            vanilla("Minecraft - Meadow", "music.overworld.meadow", Category.WORLD),
            vanilla("Minecraft - Old Growth Taiga", "music.overworld.old_growth_taiga", Category.WORLD),
            vanilla("Minecraft - Sparse Jungle", "music.overworld.sparse_jungle", Category.WORLD),
            vanilla("Minecraft - Swamp", "music.overworld.swamp", Category.WORLD),
            vanilla("Minecraft - Snowy Slopes", "music.overworld.snowy_slopes", Category.WORLD),
            vanilla("Minecraft - Stony Peaks", "music.overworld.stony_peaks", Category.WORLD),
            vanilla("Minecraft - Disc 5", "music_disc.5", Category.DISC),
            vanilla("Minecraft - Disc 11", "music_disc.11", Category.DISC),
            vanilla("Minecraft - Disc 13", "music_disc.13", Category.DISC),
            vanilla("Minecraft - Disc Blocks", "music_disc.blocks", Category.DISC),
            vanilla("Minecraft - Disc Cat", "music_disc.cat", Category.DISC),
            vanilla("Minecraft - Disc Chirp", "music_disc.chirp", Category.DISC),
            vanilla("Minecraft - Disc Far", "music_disc.far", Category.DISC),
            vanilla("Minecraft - Disc Mall", "music_disc.mall", Category.DISC),
            vanilla("Minecraft - Disc Mellohi", "music_disc.mellohi", Category.DISC),
            vanilla("Minecraft - Disc Pigstep", "music_disc.pigstep", Category.DISC),
            vanilla("Minecraft - Disc Stal", "music_disc.stal", Category.DISC),
            vanilla("Minecraft - Disc Strad", "music_disc.strad", Category.DISC),
            vanilla("Minecraft - Disc Wait", "music_disc.wait", Category.DISC),
            vanilla("Minecraft - Disc Ward", "music_disc.ward", Category.DISC),
            vanilla("Minecraft - Disc Otherside", "music_disc.otherside", Category.DISC),
            vanilla("Minecraft - Disc Relic", "music_disc.relic", Category.DISC),
            vanilla("Minecraft - Disc Creator", "music_disc.creator", Category.DISC),
            vanilla("Minecraft - Disc Creator Music Box", "music_disc.creator_music_box", Category.DISC),
            vanilla("Minecraft - Disc Precipice", "music_disc.precipice", Category.DISC));
    private static int index;
    private static Mode mode = Mode.LIST;
    private static boolean playing;
    private static ResourceLocation activeId;
    private static SimpleSoundInstance activeInstance;
    private static List<Track> tracks = List.of();
    private static boolean resourceReloading;
    private static CompletableFuture<Void> resourceReload;
    private static boolean refreshRequested;
    private static int pendingImportedIndex = -1;

    private ClientMusicPlayer() {}

    public static void refresh() {
        refreshTracks();
    }

    private static void refreshTracks() {
        List<Track> all = new ArrayList<>(VANILLA);
        List<java.nio.file.Path> imported = ClientMusicPack.importedTracks();
        for (int i = 0; i < imported.size(); i++) {
            ClientMusicPack.ResourceTrack track = ClientMusicPack.importedTrack(i);
            if (track != null) all.add(new Track(track.name(), track.id(), Category.WORLD));
        }
        tracks = List.copyOf(all);
        index = tracks.isEmpty() ? 0 : Math.min(index, tracks.size() - 1);
    }

    public static String currentName() {
        return tracks.isEmpty() ? "" : displayName(tracks.get(index));
    }

    public static String trackName(int trackIndex) {
        return trackIndex < 0 || trackIndex >= tracks.size() ? "" : displayName(tracks.get(trackIndex));
    }

    private static String displayName(Track track) {
        if (!"minecraft".equals(track.id().getNamespace())) return track.name();
        // 每次显示时按当前语言解析，切换语言后立即生效；导入曲目保留原文件名。
        return Component.translatableWithFallback(
                "endbound_ember.music_player.track." + track.id().getPath(), track.name()).getString();
    }

    public static int currentIndex() { return index; }

    public static void refreshResources() {
        requestResourceReload(-1);
    }

    private static void requestResourceReload(int importedIndex) {
        if (importedIndex >= 0) pendingImportedIndex = importedIndex;
        refreshRequested = true;
        if (resourceReloading) return;
        resourceReloading = true;
        stopSound();
        playing = false;
        if (!ClientMusicPack.refreshPack()) {
            refreshRequested = false;
            pendingImportedIndex = -1;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        try {
            resourceReload = minecraft.reloadResourcePacks();
            resourceReload.whenComplete((unused, error) -> minecraft.execute(() -> {
                resourceReload = null;
                resourceReloading = false;
                boolean shouldRefresh = refreshRequested;
                int selectedImportedIndex = pendingImportedIndex;
                refreshRequested = false;
                pendingImportedIndex = -1;
                if (error != null) {
                    EndboundEmber.LOGGER.warn("[音乐播放器] 重载客户端音乐资源失败", error);
                    return;
                }
                if (!shouldRefresh) return;
                refreshTracks();
                if (selectedImportedIndex >= 0) {
                    int selected = VANILLA.size() + selectedImportedIndex;
                    if (selected < tracks.size()) {
                        index = selected;
                        play();
                    }
                }
            }));
        } catch (RuntimeException error) {
            resourceReload = null;
            refreshRequested = false;
            pendingImportedIndex = -1;
            EndboundEmber.LOGGER.warn("[音乐播放器] 重载客户端音乐资源失败", error);
        }
    }

    public static void select(int trackIndex) {
        refresh();
        if (trackIndex < 0 || trackIndex >= tracks.size()) return;
        index = trackIndex;
        // 列表中的曲目已经在客户端资源包中注册，选择时直接播放，避免每次点击都触发资源重载。
        play();
    }

    public static Mode mode() { return mode; }
    public static boolean isPlaying() { return playing; }
    public static int size() { return tracks.size(); }

    public static boolean isVanilla(int trackIndex) {
        return trackIndex >= 0 && trackIndex < tracks.size() && tracks.get(trackIndex).id().getNamespace().equals("minecraft");
    }

    public static boolean isCategory(int trackIndex, int category) {
        if (trackIndex < 0 || trackIndex >= tracks.size()) return false;
        if (category == 0) return true;
        return tracks.get(trackIndex).category() == switch (category) {
            case 1 -> Category.MENU;
            case 2 -> Category.CREATIVE;
            case 3 -> Category.WORLD;
            case 4 -> Category.DISC;
            default -> null;
        };
    }

    public static void toggle() {
        if (playing) pause(); else play();
    }

    public static void play() {
        refresh();
        if (tracks.isEmpty()) return;
        Track track = tracks.get(index);
        stopSound();
        SoundEvent sound = SoundEvent.createVariableRangeEvent(track.id());
        activeInstance = SimpleSoundInstance.forMusic(sound);
        Minecraft.getInstance().getSoundManager().play(activeInstance);
        activeId = track.id();
        playing = true;
    }

    public static void pause() {
        stopSound();
        playing = false;
    }

    public static void previous() {
        refresh();
        if (tracks.isEmpty()) return;
        index = (index + tracks.size() - 1) % tracks.size();
        play();
    }

    public static void next() {
        refresh();
        if (tracks.isEmpty()) return;
        index = (index + 1) % tracks.size();
        play();
    }

    public static void cycleMode() {
        mode = switch (mode) {
            case SINGLE -> Mode.LIST;
            case LIST -> Mode.NONE;
            case NONE -> Mode.SINGLE;
        };
    }

    private static void stopSound() {
        if (activeId != null) Minecraft.getInstance().getSoundManager().stop(activeId, SoundSource.MUSIC);
        activeId = null;
        activeInstance = null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!playing || activeInstance == null || Minecraft.getInstance().getSoundManager().isActive(activeInstance)) return;
        if (mode == Mode.SINGLE) play();
        else if (mode == Mode.LIST && index + 1 < tracks.size()) {
            index++;
            play();
        } else if (mode == Mode.LIST) {
            index = 0;
            play();
        } else {
            playing = false;
            activeId = null;
        }
    }
}
