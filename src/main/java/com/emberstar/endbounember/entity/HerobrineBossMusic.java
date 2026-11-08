package com.emberstar.endbounember.entity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Him Boss 战背景音乐。
 * <p>玩家进入 Boss 一定范围后开始播放，逃离范围或 Boss 倒下时停止。
 * 曲目时长直接从 OGG 文件里解析（granule position / 采样率），
 * 按解析出的时长重新触发同一首曲子实现无缝循环；只有解析失败时才用
 * {@link Config#HEROBRINE_BOSS_MUSIC_LOOP_SECONDS} 兜底。</p>
 * <p>曲目来源：玩家放进导入目录的任意 OGG 优先（可在配置的歌单里按文件名 A-Z 点选），
 * 没有导入就不再覆盖，回退到 mod 自带的默认曲目。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class HerobrineBossMusic {
    private static final ResourceLocation MUSIC_ID =
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "herobrine_boss_music");

    /** 玩家 UUID -> 距离下一次重新触发音乐的 tick 数 */
    private static final Map<UUID, Integer> REMAINING = new HashMap<>();

    /** 自带默认曲目在 mod 内的路径 */
    private static final String DEFAULT_MUSIC_PATH = "assets/endbound_ember/sounds/music/herobrine_boss_music.ogg";

    /**
     * 资源包里固定引用的文件名。
     * <p>玩家导入的 OGG 可以任意命名，客户端会把选中的曲目复制到这个路径再覆盖默认曲目。</p>
     */
    private static final String SERVED_FILE_NAME = "herobrine_boss_music.ogg";

    /** 生成的资源包放在玩家目录下的子文件夹里，避免和玩家自己的 OGG 混在一起 */
    private static final String GENERATED_DIR_NAME = ".pack";

    /** 记录玩家在歌单里点选的曲目（相对导入目录的路径） */
    private static final String SELECTION_FILE_NAME = ".selected_track";

    private HerobrineBossMusic() {
    }

    /** 玩家导入目录：任意位置的 OGG 都会被歌单收录 */
    public static Path importedPackRoot() {
        return FMLPaths.CONFIGDIR.get().resolve("endbound_ember").resolve("boss_music");
    }

    /** 自动生成的客户端资源包根目录 */
    public static Path generatedPackRoot() {
        return importedPackRoot().resolve(GENERATED_DIR_NAME);
    }

    /** 资源包里存放音效的目录 */
    public static Path importedMusicDir() {
        return generatedPackRoot().resolve("assets").resolve("endbound_ember").resolve("sounds").resolve("music");
    }

    /** 资源包实际引用的曲目路径，内容来自玩家导入曲目的副本 */
    public static Path importedMusicFile() {
        return importedMusicDir().resolve(SERVED_FILE_NAME);
    }

    /** 歌单选择记录文件 */
    public static Path selectionFile() {
        return importedPackRoot().resolve(SELECTION_FILE_NAME);
    }

    /**
     * 导入目录里所有可用的 OGG（文件名不限），按文件名 A-Z 排序。
     * <p>自动生成的资源包副本所在目录不参与扫描，避免把副本当成玩家曲目。</p>
     */
    public static List<Path> importedOggSources() {
        Path root = importedPackRoot();
        if (!Files.isDirectory(root)) return List.of();

        Path generated = generatedPackRoot().toAbsolutePath().normalize();
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !path.toAbsolutePath().normalize().startsWith(generated))
                    .filter(HerobrineBossMusic::isOgg)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static boolean isOgg(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg");
    }

    /** 歌单里当前点选的曲目；文件被删除或改名后自动失效 */
    public static Optional<Path> selectedTrack() {
        Path file = selectionFile();
        if (!Files.isRegularFile(file)) return Optional.empty();

        try {
            String relative = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (relative.isEmpty()) return Optional.empty();

            Path root = importedPackRoot().toAbsolutePath().normalize();
            Path resolved = root.resolve(relative).normalize();
            if (!resolved.startsWith(root) || !isOgg(resolved) || !Files.isRegularFile(resolved)) {
                return Optional.empty();
            }
            return Optional.of(resolved);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * 记录歌单选择。
     * <p>传入 {@code null} 表示不使用导入曲目，回退到 mod 自带默认曲目。</p>
     */
    public static void selectTrack(Path track) {
        Path file = selectionFile();
        try {
            if (track == null) {
                Files.deleteIfExists(file);
                return;
            }
            Path root = importedPackRoot().toAbsolutePath().normalize();
            Files.createDirectories(root);
            Files.writeString(file, root.relativize(track.toAbsolutePath().normalize()).toString(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            EndboundEmber.LOGGER.warn("[BOSS 战配乐] 保存歌单选择失败: {}", file, e);
        }
    }

    /** 实际应该生效的曲目：优先用歌单选中的，没选过就用文件名字典序第一首 */
    public static Optional<Path> activeTrack() {
        Optional<Path> selected = selectedTrack();
        if (selected.isPresent()) return selected;

        List<Path> sources = importedOggSources();
        return sources.isEmpty() ? Optional.empty() : Optional.of(sources.get(0));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (!player.isAlive() || !Config.HEROBRINE_BOSS_MUSIC_ENABLED.get() || !bossNearby(player)) {
            stop(player);
            return;
        }

        Integer remaining = REMAINING.get(player.getUUID());
        if (remaining == null || remaining <= 0) {
            play(player);
            REMAINING.put(player.getUUID(), (int) detectLoopTicks());
            return;
        }
        REMAINING.put(player.getUUID(), remaining - 1);
    }

    /** 玩家所在世界里是否有存活的本体 Boss 在音乐半径内 */
    private static boolean bossNearby(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return false;

        double range = Config.HEROBRINE_BOSS_MUSIC_RANGE.get();
        double rangeSqr = range * range;
        List<Herobrine> bosses = level.getEntitiesOfClass(Herobrine.class,
                player.getBoundingBox().inflate(range),
                boss -> !boss.isPhantom() && boss.isAlive());
        for (Herobrine boss : bosses) {
            if (boss.distanceToSqr(player) <= rangeSqr) return true;
        }
        return false;
    }

    /** 优先读取 OGG 的实际时长，读不到时退回配置里的兜底值 */
    private static long detectLoopTicks() {
        double seconds = readOggSeconds();
        if (seconds > 1.0D) {
            // 略微提前重触发，避免出现明显静音间隙
            return Math.max(20L, (long) (seconds * 20.0D));
        }
        return Math.max(20, Config.HEROBRINE_BOSS_MUSIC_LOOP_SECONDS.get() * 20);
    }

    /**
     * 解析当前生效曲目的时长：先找玩家导入的文件，再退回自带曲目。
     * <p>每次都重新解析，玩家换歌后无需重启存档。</p>
     * 文件缺失或格式不支持时返回 0。
     */
    private static double readOggSeconds() {
        double served = readSeconds(importedMusicFile());
        if (served > 0.0D) return served;

        Path active = activeTrack().orElse(null);
        if (active != null) {
            double seconds = readSeconds(active);
            if (seconds > 0.0D) return seconds;
        }

        try (InputStream in = HerobrineBossMusic.class.getClassLoader().getResourceAsStream(DEFAULT_MUSIC_PATH)) {
            if (in == null) return 0.0D;
            return parseOggSeconds(in.readAllBytes());
        } catch (IOException e) {
            return 0.0D;
        }
    }

    /** 读取单个 OGG 文件的时长；文件不存在或解析失败时返回 0 */
    private static double readSeconds(Path path) {
        if (!Files.isRegularFile(path)) return 0.0D;
        try (InputStream in = Files.newInputStream(path)) {
            return parseOggSeconds(in.readAllBytes());
        } catch (IOException e) {
            return 0.0D;
        }
    }

    /** 解析 Vorbis 音频时长：遍历 OGG 页取最后一个 granule position，再除以采样率 */
    private static double parseOggSeconds(byte[] data) {
        int serial = -1;
        int sampleRate = 0;
        long lastGranule = 0L;

        int pos = 0;
        while (pos + 27 <= data.length) {
            if (data[pos] != 'O' || data[pos + 1] != 'g' || data[pos + 2] != 'g' || data[pos + 3] != 'S') {
                pos++;
                continue;
            }

            long granule = readLongLE(data, pos + 6);
            int pageSerial = readIntLE(data, pos + 14);
            int segments = data[pos + 26] & 0xFF;
            int tableStart = pos + 27;
            int payloadStart = tableStart + segments;
            if (payloadStart > data.length) break;

            int payloadLength = 0;
            for (int i = 0; i < segments; i++) {
                payloadLength += data[tableStart + i] & 0xFF;
            }
            int pageEnd = payloadStart + payloadLength;
            if (pageEnd > data.length) break;

            if (serial < 0) serial = pageSerial;
            if (pageSerial == serial) {
                // granule 为 -1 表示该页没有结束任何音频包
                if (granule >= 0L && granule > lastGranule) lastGranule = granule;
                if (sampleRate == 0 && payloadLength >= 16 && (data[payloadStart] & 0xFF) == 1
                        && data[payloadStart + 1] == 'v' && data[payloadStart + 2] == 'o'
                        && data[payloadStart + 3] == 'r' && data[payloadStart + 4] == 'b'
                        && data[payloadStart + 5] == 'i' && data[payloadStart + 6] == 's') {
                    // Vorbis 识别头：版本(4) 声道数(1) 采样率(4LE)
                    sampleRate = readIntLE(data, payloadStart + 12);
                }
            }
            pos = pageEnd;
        }

        if (sampleRate <= 0 || lastGranule <= 0L) return 0.0D;
        return lastGranule / (double) sampleRate;
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }

    private static long readLongLE(byte[] data, int offset) {
        long value = 0L;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (data[offset + i] & 0xFFL);
        }
        return value;
    }

    /** 先清空音乐声道（含原版背景音乐），再从头播放 Boss 曲目 */
    private static void play(ServerPlayer player) {
        float volume = Config.HEROBRINE_BOSS_MUSIC_VOLUME.get().floatValue();
        player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
        player.connection.send(new ClientboundSoundPacket(EndboundEmber.HEROBRINE_BOSS_MUSIC,
                SoundSource.MUSIC, player.getX(), player.getY(), player.getZ(),
                volume, 1.0F, player.getRandom().nextLong()));
    }

    /** 停止 Boss 音乐；只有确实处于播放状态时才发包，避免每 tick 刷包 */
    private static void stop(ServerPlayer player) {
        if (REMAINING.remove(player.getUUID()) == null) return;
        player.connection.send(new ClientboundStopSoundPacket(MUSIC_ID, SoundSource.MUSIC));
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        REMAINING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            stop(player);
        }
    }
}
