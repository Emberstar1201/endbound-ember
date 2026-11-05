package com.emberstar.endbounember.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.TimeFreezeClient;
import com.emberstar.endbounember.effect.ModEffects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 时光光环的三个核心技能。
 *
 * <p>变慢与暂停都以玩家所在区块为中心，半径由配置 {@code timeHaloSkillChunkRadius} 决定：</p>
 * <ul>
 * <li>变慢：范围内生物持续获得缓慢，实体位移速度按 {@code timeHaloSlowFactor} 缩放；
 * 实体仍然每 tick 正常推进，服务端与客户端位置同步，不会跳位</li>
 * <li>暂停：范围内非玩家实体整 tick 跳过（见 mixin），区块随机刻与方块实体也一并停住，
 * 只有佩戴者能行动，整体呈现为除佩戴者外万物凝滞；客户端同步冻结实体与粒子，
 * 弹射物保留速度，解除暂停后继续飞行</li>
 * <li>倒流：第一次按键留下锚点并快照周围实体，第二次按键把玩家与仍在场的实体送回那一刻</li>
 * </ul>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class TimeHaloSkills {

    /** 玩家的当前技能状态，三者互斥 */
    public enum Mode {
        NONE, SLOW, STOP
    }

    /** 时间锚点：玩家位置朝向 + 当时附近实体的快照 */
    private record Anchor(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot,
            List<EntitySnapshot> entities) {
    }

    /** 单个实体的倒流快照：位置、朝向与生命值 */
    private record EntitySnapshot(UUID id, Vec3 pos, float yRot, float xRot, float health) {
    }

    private static final Map<UUID, Mode> MODES = new HashMap<>();
    private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();
    /** 每 tick 缓存的暂停中心区块，避免 isFrozen 里反复计算 ChunkPos */
    private static final Map<ResourceKey<Level>, List<ChunkPos>> STOP_CENTERS = new HashMap<>();

    /** 单个锚点最多快照的实体数，防止大范围内实体过多导致内存暴涨 */
    private static final int MAX_SNAPSHOT_ENTITIES = 256;

    /** 标记效果每次刷新的时长（tick），略长于 1 秒以便稳定续期 */
    private static final int MARKER_DURATION = 40;

    private TimeHaloSkills() {
    }

    /** 客户端技能键入口：0 变慢、1 暂停、2 倒流 */
    public static void handle(ServerPlayer player, int action) {
        switch (action) {
            case 0 -> toggleSlow(player);
            case 1 -> toggleStop(player);
            case 2 -> toggleRewind(player);
            default -> {
            }
        }
    }

    public static void toggleSlow(ServerPlayer player) {
        toggle(player, Mode.SLOW);
    }

    public static void toggleStop(ServerPlayer player) {
        toggle(player, Mode.STOP);
    }

    /** 同一技能再次按下即关闭，切换技能时替换掉原本的状态 */
    private static void toggle(ServerPlayer player, Mode mode) {
        if (!TimeTurbulenceLevels.wearsTimeHalo(player)) return;
        Mode current = MODES.getOrDefault(player.getUUID(), Mode.NONE);
        setMode(player, current == mode ? Mode.NONE : mode);
    }

    private static void setMode(ServerPlayer player, Mode mode) {
        UUID id = player.getUUID();
        if (mode == Mode.NONE) {
            MODES.remove(id);
            player.removeEffect(ModEffects.TIME_SLOW);
            player.removeEffect(ModEffects.TIME_STOP);
            return;
        }
        MODES.put(id, mode);
        if (mode == Mode.STOP) {
            // 暂停生效的瞬间抹掉已有的位移，避免实体沿惯性继续漂移；
            // 弹射物例外，保留速度，解除暂停后才能继续沿原轨迹飞出去
            clearVelocityAround(player);
        }
        // 标记效果不显示图标，只供客户端光环读秒针速度
        MobEffectInstance marker = new MobEffectInstance(
                mode == Mode.SLOW ? ModEffects.TIME_SLOW : ModEffects.TIME_STOP,
                MARKER_DURATION, 0, false, false, false);
        player.addEffect(marker);
    }

    /** 倒流：第一次按下留下锚点与实体快照，第二次按下把这一带送回锚点时刻 */
    public static void toggleRewind(ServerPlayer player) {
        if (!TimeTurbulenceLevels.wearsTimeHalo(player)) return;
        UUID id = player.getUUID();
        Anchor previous = ANCHORS.remove(id);
        if (previous == null) {
            int radius = Config.TIME_HALO_SKILL_CHUNK_RADIUS.getAsInt();
            ANCHORS.put(id, createAnchor(player, radius));
            markAnchor(player.level(), player.getX(), player.getY(), player.getZ());
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel target = server.getLevel(previous.dimension());
        if (target == null) return;
        // 先保证落点区块就绪，否则传送瞬间脚下还是空的
        target.getChunk(BlockPos.containing(previous.x(), previous.y(), previous.z()).getX() >> 4,
                BlockPos.containing(previous.x(), previous.y(), previous.z()).getZ() >> 4,
                ChunkStatus.FULL, true);
        // 先把当时周围的实体送回原位，再传送玩家
        restoreEntities(target, previous.entities());
        player.stopRiding();
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(target, previous.x(), previous.y(), previous.z(), previous.yRot(), previous.xRot());
        markAnchor(target, previous.x(), previous.y(), previous.z());
    }

    /** 记录锚点时刻：玩家位置朝向 + 范围内实体快照 */
    private static Anchor createAnchor(ServerPlayer player, int radius) {
        List<EntitySnapshot> snapshots = new ArrayList<>();
        for (Entity entity : entitiesAround(player, radius)) {
            if (snapshots.size() >= MAX_SNAPSHOT_ENTITIES) break;
            float health = entity instanceof LivingEntity living ? living.getHealth() : -1.0F;
            snapshots.add(new EntitySnapshot(entity.getUUID(), entity.position(),
                    entity.getYRot(), entity.getXRot(), health));
        }
        return new Anchor(player.level().dimension(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                List.copyOf(snapshots));
    }

    /** 把快照里仍在场的实体送回锚点时刻的位置与生命值 */
    private static void restoreEntities(ServerLevel target, List<EntitySnapshot> snapshots) {
        for (EntitySnapshot snapshot : snapshots) {
            Entity entity = target.getEntities().get(snapshot.id());
            if (entity == null) continue;
            entity.moveTo(snapshot.pos().x, snapshot.pos().y, snapshot.pos().z, snapshot.yRot(), snapshot.xRot());
            entity.setDeltaMovement(Vec3.ZERO);
            entity.resetFallDistance();
            if (entity instanceof LivingEntity living && snapshot.health() > 0.0F) {
                living.setHealth(Math.min(snapshot.health(), living.getMaxHealth()));
            }
        }
    }

    /** 每 tick 前缓存暂停中心，供实体与区块的拦截判断使用 */
    @SubscribeEvent
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        STOP_CENTERS.clear();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<ChunkPos> centers = new ArrayList<>();
            for (ServerPlayer player : level.players()) {
                if (MODES.getOrDefault(player.getUUID(), Mode.NONE) == Mode.STOP) {
                    centers.add(player.chunkPosition());
                }
            }
            if (!centers.isEmpty()) {
                STOP_CENTERS.put(level.dimension(), centers);
            }
        }
    }

    /** 是否被某个正在暂停的玩家笼罩，供 ServerLevel 的实体 tick 拦截判断 */
    public static boolean isFrozen(ServerLevel level, Entity entity) {
        if (entity instanceof Player) return false;
        // 掉落物与经验球不冻结：否则暂停期间农场产物永远不会消失，会无限堆积
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) return false;
        // 玩家正在乘坐的载具不冻结，避免玩家与载具位置不同步
        if (entity.isVehicle() && entity.getFirstPassenger() instanceof Player) return false;
        return withinArea(STOP_CENTERS.get(level.dimension()), entity.getBlockX(), entity.getBlockZ());
    }

    /** 该区块是否处于暂停范围，供 ServerLevel 的区块 tick 拦截判断 */
    public static boolean isChunkFrozen(ServerLevel level, ChunkPos pos) {
        return withinArea(STOP_CENTERS.get(level.dimension()), pos.getMinBlockX(), pos.getMinBlockZ());
    }

    /** 该方块实体是否处于暂停范围，供方块实体 tick 拦截判断 */
    public static boolean isBlockPosFrozen(ServerLevel level, BlockPos pos) {
        return withinArea(STOP_CENTERS.get(level.dimension()), pos.getX(), pos.getZ());
    }

    /** 方块实体是否暂停：服务端查缓存范围，客户端查本地重建的范围 */
    public static boolean isBlockFrozen(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return TimeFreezeClient.isFrozen(pos);
        }
        return level instanceof ServerLevel server && isBlockPosFrozen(server, pos);
    }

    /**
     * 变慢：范围内生物获得缓慢效果，所有实体的位移速度按配置倍率缩小。
     * <p>实体仍然每个 tick 正常推进，位置始终与服务端同步，
     * 因此不会出现弹射物落点来回跳位的问题。</p>
     */
    private static void applySlow(ServerPlayer player) {
        int radius = Config.TIME_HALO_SKILL_CHUNK_RADIUS.getAsInt();
        float factor = (float) (double) Config.TIME_HALO_SLOW_FACTOR.get();
        for (Entity entity : entitiesAround(player, radius)) {
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 3, false, false, false));
            }
            Vec3 delta = entity.getDeltaMovement();
            if (delta.lengthSqr() > 1.0E-6D) {
                entity.setDeltaMovement(delta.scale(factor));
            }
        }
    }

    /** 直接用方块坐标做区块比较，不再分配 ChunkPos */
    private static boolean withinArea(List<ChunkPos> centers, int blockX, int blockZ) {
        if (centers == null || centers.isEmpty()) return false;
        int radius = Config.TIME_HALO_SKILL_CHUNK_RADIUS.getAsInt();
        int cx = SectionPos.blockToSectionCoord(blockX);
        int cz = SectionPos.blockToSectionCoord(blockZ);
        for (ChunkPos center : centers) {
            if (Math.abs(center.x - cx) <= radius && Math.abs(center.z - cz) <= radius) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        boolean awardTick = event.getServer().getTickCount() % 20 == 0;
        // 锚点常态可见，玩家随时知道倒流会回到哪里
        if (event.getServer().getTickCount() % 10 == 0) {
            showAnchors(event.getServer());
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            boolean wearing = TimeTurbulenceLevels.wearsTimeHalo(player);
            Mode mode = MODES.getOrDefault(player.getUUID(), Mode.NONE);
            if (mode != Mode.NONE) {
                if (!wearing) {
                    // 摘下光环后技能立刻失效
                    setMode(player, Mode.NONE);
                    mode = Mode.NONE;
                } else {
                    refreshMarker(player, mode);
                }
            }
            if (mode == Mode.SLOW) {
                applySlow(player);
            }
            // 佩戴期间附近的作物 / 酿造台 / 炉子获得额外流速；暂停时则一起停住
            if (wearing && mode != Mode.STOP) {
                TimeHaloAcceleration.tickPlayer(player);
            } else {
                TimeHaloAcceleration.clear(player);
            }
            // 「第四维度的常客」：举行仪式获得光环并佩戴上即可
            if (awardTick && wearing && !ModAdvancements.hasDone(player,
                    ModAdvancements.VISITOR_OF_THE_FOURTH_DIMENSION)) {
                ModAdvancements.award(player, ModAdvancements.VISITOR_OF_THE_FOURTH_DIMENSION);
            }
        }
    }

    private static void refreshMarker(ServerPlayer player, Mode mode) {
        MobEffectInstance current = player.getEffect(
                mode == Mode.SLOW ? ModEffects.TIME_SLOW : ModEffects.TIME_STOP);
        if (current == null || current.getDuration() < MARKER_DURATION / 2) {
            player.addEffect(new MobEffectInstance(
                    mode == Mode.SLOW ? ModEffects.TIME_SLOW : ModEffects.TIME_STOP,
                    MARKER_DURATION, 0, false, false, false));
        }
    }

    /** 只给锚点主人显示：脚下一圈逆流星光与缓慢上升的星点 */
    private static void showAnchors(MinecraftServer server) {
        for (Map.Entry<UUID, Anchor> entry : ANCHORS.entrySet()) {
            Anchor anchor = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || owner.level().dimension() != anchor.dimension()) continue;
            ServerLevel level = (ServerLevel) owner.level();
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * 2.0D / 10.0D * i;
                level.sendParticles(owner, ParticleTypes.END_ROD, true,
                        anchor.x() + Math.cos(angle) * 0.7D, anchor.y() + 0.15D,
                        anchor.z() + Math.sin(angle) * 0.7D, 1, 0.0D, 0.02D, 0.0D, 0.0D);
            }
            level.sendParticles(owner, ParticleTypes.END_ROD, true,
                    anchor.x(), anchor.y() + 1.0D, anchor.z(), 2, 0.05D, 0.4D, 0.05D, 0.01D);
        }
    }

    /** 暂停生效瞬间：把范围内实体的速度清零，之后它们不再 tick；弹射物保留动量 */
    private static void clearVelocityAround(ServerPlayer player) {
        int radius = Config.TIME_HALO_SKILL_CHUNK_RADIUS.getAsInt();
        for (Entity entity : entitiesAround(player, radius)) {
            if (entity instanceof Projectile) continue;
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    /** 以玩家所在区块为中心、半径 radius 个区块内的所有非玩家实体 */
    private static Iterable<Entity> entitiesAround(ServerPlayer player, int radius) {
        double range = (radius + 1) * 16.0D;
        AABB box = new AABB(
                player.getX() - range, player.getY() - range, player.getZ() - range,
                player.getX() + range, player.getY() + range, player.getZ() + range);
        ChunkPos center = player.chunkPosition();
        return player.level().getEntities(player, box, entity -> !(entity instanceof Player)
                && withinChunks(center, entity.chunkPosition(), radius));
    }

    private static boolean withinChunks(ChunkPos center, ChunkPos target, int radius) {
        return Math.abs(center.x - target.x) <= radius && Math.abs(center.z - target.z) <= radius;
    }

    /** 落点表现：锚点处升起一圈逆流的粒子并响一声轻响 */
    private static void markAnchor(Level level, double x, double y, double z) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ParticleTypes.END_ROD, x, y + 1.0D, z, 24, 0.35D, 0.7D, 0.35D, 0.02D);
        serverLevel.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 0.6F);
    }
}
