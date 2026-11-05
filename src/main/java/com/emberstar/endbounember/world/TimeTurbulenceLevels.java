package com.emberstar.endbounember.world;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.compat.CuriosCompat;
import com.emberstar.endbounember.mixin.MinecraftServerAccessor;
import com.emberstar.endbounember.network.TimeTurbulenceTogglePayload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 时间乱流维度：每个玩家一份独立实例。
 * <p>
 * 维度键为 {@code endbound_ember:time_turbulence/<玩家 UUID>}，运行时用
 * {@code MinecraftServer#forgeGetWorldMap()} 动态注册，因此不需要（也不能）写
 * {@code dimension/*.json}，否则会额外生成一份所有人共享的维度。
 * <p>
 * 地形为高架石英平台，下方由平滑石与基岩支撑；天空由客户端 {@code TimeTurbulenceSkyEffects} 接管。
 */
public final class TimeTurbulenceLevels {
    private static final String PATH_PREFIX = "time_turbulence/";

    /** 维度类型的注册键，与 data/endbound_ember/dimension_type/time_turbulence.json 对应 */
    public static final ResourceKey<DimensionType> DIMENSION_TYPE = ResourceKey.create(
            Registries.DIMENSION_TYPE, EndboundEmber.id("time_turbulence"));

    /** 地板下方的基岩层数 */
    public static final int BEDROCK_LAYERS = 4;
    /** 地板下方的平滑石层数 */
    public static final int SMOOTH_STONE_LAYERS = 12;
    /** 高架石英地板所在高度 */
    public static final int GROUND_Y = BEDROCK_LAYERS + SMOOTH_STONE_LAYERS;
    /** 玩家进入后的落脚点，固定在世界原点上方 */
    private static final double SPAWN_X = 0.5D;
    private static final double SPAWN_Z = 0.5D;

    /** 时之锚结构模板（11×11×50，顶部展示台放着时间光环） */
    private static final ResourceLocation CHRONO_ANCHOR = EndboundEmber.id("chrono_anchor");
    private static final ResourceLocation CHRONO_ANCHOR_RESOURCE = EndboundEmber.id("structures/chrono_anchor.nbt");
    /**
     * 结构原点的 Y 偏移：结构文件里结构方块记录的相对位置就是 Y = -2，
     * 即结构原点比地面站立高度低 2 格——最底下一层埋进地基，内部地面正好与石英地板上表面齐平。
     */
    private static final int CHRONO_ANCHOR_Y_OFFSET = -2;
    /** 时之锚的候选落点（相对出生点的水平偏移），依次取第一个没有被占用、也不会压住玩家建筑的位置 */
    private static final int[][] CHRONO_ANCHOR_OFFSETS = {
            {7, -5}, {-17, -5}, {7, 12}, {7, -22}
    };

    private static final String RETURN_DIMENSION = "endbound_ember:time_turbulence_return_dimension";
    private static final String RETURN_X = "endbound_ember:time_turbulence_return_x";
    private static final String RETURN_Y = "endbound_ember:time_turbulence_return_y";
    private static final String RETURN_Z = "endbound_ember:time_turbulence_return_z";
    private static final String RETURN_YAW = "endbound_ember:time_turbulence_return_yaw";
    private static final String RETURN_PITCH = "endbound_ember:time_turbulence_return_pitch";

    /** 玩家上次站在自己乱流维度里的位置：重进时直接回到自己的庇护所 */
    private static final String LAST_X = "endbound_ember:time_turbulence_last_x";
    private static final String LAST_Y = "endbound_ember:time_turbulence_last_y";
    private static final String LAST_Z = "endbound_ember:time_turbulence_last_z";
    private static final String LAST_YAW = "endbound_ember:time_turbulence_last_yaw";
    private static final String LAST_PITCH = "endbound_ember:time_turbulence_last_pitch";

    /** 动态维度不参与出生点区块预生成，进度监听器做成空实现 */
    private static final ChunkProgressListener NOOP_LISTENER = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(ChunkPos center) {
        }

        @Override
        public void onStatusChange(ChunkPos chunkPos, @Nullable ChunkStatus chunkStatus) {
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    };

    private TimeTurbulenceLevels() {
    }

    /** 判定一个世界是否为时间乱流维度（不区分属于哪个玩家） */
    public static boolean isTimeTurbulence(Level level) {
        return ownerOf(level).isPresent();
    }

    /** 该玩家专属维度的世界键 */
    public static ResourceKey<Level> keyFor(UUID playerId) {
        return ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, PATH_PREFIX + playerId));
    }

    /** 该时间乱流维度所属的玩家；不是时间乱流维度时返回 empty */
    public static Optional<UUID> ownerOf(Level level) {
        return ownerOf(level.dimension());
    }

    /** 该时间乱流维度键所属的玩家；不是时间乱流维度时返回 empty */
    public static Optional<UUID> ownerOf(ResourceKey<Level> key) {
        ResourceLocation id = key.location();
        if (!EndboundEmber.MODID.equals(id.getNamespace()) || !id.getPath().startsWith(PATH_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(id.getPath().substring(PATH_PREFIX.length())));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** 是否佩戴了时光光环（头部装备或饰品栏） */
    public static boolean wearsTimeHalo(Player player) {
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(EndboundEmber.TIME_HALO.get())) {
            return true;
        }
        return ModList.get().isLoaded("curios")
                && CuriosCompat.isEquipped(player, EndboundEmber.TIME_HALO.get());
    }

    /** 轮盘选择后的通用维度传送：0=主世界、1=下界、2=末地、3=时间乱流切换 */
    public static void goTo(ServerPlayer player, int direction) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        switch (direction) {
            case TimeTurbulenceTogglePayload.OVERWORLD -> teleportToSpawn(player, server.overworld());
            case TimeTurbulenceTogglePayload.NETHER -> {
                ServerLevel nether = server.getLevel(Level.NETHER);
                if (nether != null) {
                    teleportToSpawn(player, nether);
                }
            }
            case TimeTurbulenceTogglePayload.END -> {
                ServerLevel end = server.getLevel(Level.END);
                if (end != null) {
                    teleportToSpawn(player, end);
                }
            }
            case TimeTurbulenceTogglePayload.TIME_TURBULENCE -> toggle(player);
            default -> EndboundEmber.LOGGER.warn("[时光光环] 未知的传送方向参数：{}", direction);
        }
    }

    /** 佩戴时光光环时按 I 键触发的进出切换 */
    public static void toggle(ServerPlayer player) {
        if (isTimeTurbulence(player.level())) {
            leave(player);
        } else {
            enter(player);
        }
    }

    /** 进入玩家自己的时间乱流维度，同时记录返回点 */
    public static void enter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        ServerLevel target = getOrCreate(server, player.getUUID());
        if (player.level() == target) {
            return;
        }
        // 保存坐标可能来自旧版低地板；恢复前验证原位置，并在同一 X/Z 列重找安全地面
        CompoundTag data = player.getPersistentData();
        Optional<BlockPos> landing = data.contains(LAST_X)
                ? findSafeLanding(target, data.getDouble(LAST_X), data.getDouble(LAST_Y), data.getDouble(LAST_Z))
                : Optional.empty();
        if (landing.isEmpty()) {
            landing = findSafeNear(target, BlockPos.containing(SPAWN_X, GROUND_Y, SPAWN_Z));
        }
        if (landing.isEmpty()) {
            notifyNoLanding(player);
            return;
        }
        saveReturnPoint(player);
        BlockPos pos = landing.get();
        teleport(player, target,
                pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                data.contains(LAST_X) ? data.getFloat(LAST_YAW) : player.getYRot(),
                data.contains(LAST_X) ? data.getFloat(LAST_PITCH) : player.getXRot());
    }

    /** 验证请求位置；不安全时改在该 X/Z 列的地表寻找站立点 */
    private static Optional<BlockPos> findSafeLanding(ServerLevel level, double x, double y, double z) {
        BlockPos requested = BlockPos.containing(x, y, z);
        level.getChunk(requested.getX() >> 4, requested.getZ() >> 4, ChunkStatus.FULL, true);
        if (isSafeStandingSpot(level, requested)) {
            return Optional.of(requested);
        }
        return findSafeColumn(level, requested.getX(), requested.getZ());
    }

    /** 被维度拥有者允许后，把玩家送进他人的时间乱流维度，并修正失效的旧落点 */
    public static void enterAsGuest(ServerPlayer player, ServerLevel target,
            double x, double y, double z, float yRot, float xRot) {
        Optional<BlockPos> landing = findSafeLanding(target, x, y, z);
        if (landing.isEmpty()) {
            landing = findSafeNear(target, target.getSharedSpawnPos());
        }
        if (landing.isEmpty()) {
            notifyNoLanding(player);
            return;
        }
        saveReturnPoint(player);
        BlockPos pos = landing.get();
        teleport(player, target,
                pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yRot, xRot);
    }

    /** 离开时间乱流维度，回到进入前的位置 */
    public static void leave(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        // 先把自己庇护所里的位置记下来，下次按 I 键回来还能站在原地
        saveOwnPosition(player);

        // 优先使用进入乱流时记录的返回坐标，但先确认那里仍然站得住
        CompoundTag data = player.getPersistentData();
        if (data.contains(RETURN_DIMENSION)) {
            ServerLevel saved = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(data.getString(RETURN_DIMENSION))));
            if (saved != null && isSafeStandingSpot(saved, BlockPos.containing(
                    data.getDouble(RETURN_X), data.getDouble(RETURN_Y), data.getDouble(RETURN_Z)))) {
                clearReturnPoint(player);
                teleport(player, saved,
                        data.getDouble(RETURN_X), data.getDouble(RETURN_Y), data.getDouble(RETURN_Z),
                        data.getFloat(RETURN_YAW), data.getFloat(RETURN_PITCH));
                return;
            }
        }

        // 没有记录、维度不可用或返回点已不安全：安全搜索主世界出生点
        ServerLevel target = server.overworld();
        Optional<BlockPos> safe = findSafeNear(target, target.getSharedSpawnPos());
        clearReturnPoint(player);
        if (safe.isPresent()) {
            BlockPos pos = safe.get();
            teleport(player, target,
                    pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
        } else {
            notifyNoLanding(player);
        }
    }

    /** 把玩家送到目标维度的一个安全落点（末地固定黑曜石平台，其他维度优先床/重生锚与陆地） */
    private static void teleportToSpawn(ServerPlayer player, ServerLevel target) {
        if (player.level() == target) {
            // 已经在目标维度：什么都不做，避免传送到出生点把玩家送飞
            return;
        }
        // 末地：直接落到原版黑曜石平台上，平台不存在或被拆掉时就地重建
        if (target.dimension() == Level.END) {
            teleportToEndPlatform(player, target);
            return;
        }
        // 1. 优先玩家在目标维度上的床/重生锚位置
        BlockPos respawn = player.getRespawnPosition();
        if (respawn != null && player.getRespawnDimension().equals(target.dimension())) {
            for (BlockPos candidate : new BlockPos[] { respawn.above(), respawn }) {
                if (isSafeStandingSpot(target, candidate)) {
                    teleport(player, target,
                            candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D,
                            player.getYRot(), player.getXRot());
                    return;
                }
            }
        }
        // 2. 在该维度共享出生点附近按地表搜索陆地落点
        Optional<BlockPos> safe = findSafeNear(target, target.getSharedSpawnPos());
        if (safe.isPresent()) {
            BlockPos pos = safe.get();
            teleport(player, target,
                    pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            return;
        }
        // 3. 找不到可靠落点就取消传送，绝不把玩家丢进虚空或石头里
        notifyNoLanding(player);
    }

    /** 末地落点：复刻原版首次进入末地时的黑曜石平台 */
    private static void teleportToEndPlatform(ServerPlayer player, ServerLevel end) {
        Vec3 landing = ServerLevel.END_SPAWN_POINT.getBottomCenter();
        // 平台以落点所在方块的下方一格为中心重建，与 EndPortalBlock 的处理保持一致
        EndPlatformFeature.createEndPlatform(end, BlockPos.containing(landing).below(), true);
        Vec3 pos = landing.subtract(0.0D, 1.0D, 0.0D);
        teleport(player, end, pos.x, pos.y, pos.z, Direction.WEST.toYRot(), 0.0F);
    }

    /**
     * 检查一个位置是否适合玩家站立：
     * <ul>
     * <li>脚下一格有碰撞支撑（石头、木板、台阶等），而不是岩浆、岩浆块、仙人掌等伤害方块</li>
     * <li>站立位置及上方 1 格都没有方块阻挡（玩家 1.8 格身高刚好占这两格）</li>
     * <li>站立位置及上方 1 格都不在流体、火焰或细雪里</li>
     * </ul>
     */
    private static boolean isSafeStandingSpot(Level level, BlockPos feet) {
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        if (ground.getCollisionShape(level, below).isEmpty()) {
            return false;
        }
        if (ground.is(Blocks.LAVA) || ground.is(Blocks.MAGMA_BLOCK) || ground.is(Blocks.CACTUS)
                || ground.is(Blocks.FIRE) || ground.is(Blocks.SOUL_FIRE)
                || ground.is(Blocks.CAMPFIRE) || ground.is(Blocks.SOUL_CAMPFIRE)
                || ground.is(Blocks.POWDER_SNOW)) {
            return false;
        }
        return isStandableSpace(level, feet) && isStandableSpace(level, feet.above());
    }

    /** 单格空间是否能容纳玩家：无碰撞、无流体、不是火焰或细雪 */
    private static boolean isStandableSpace(Level level, BlockPos pos) {
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
            return false;
        }
        if (!level.getFluidState(pos).isEmpty()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return !state.is(Blocks.FIRE) && !state.is(Blocks.SOUL_FIRE) && !state.is(Blocks.POWDER_SNOW);
    }

    /** 在单个竖直列上自上而下寻找地表落点 */
    private static Optional<BlockPos> findSafeColumn(ServerLevel level, int x, int z) {
        // 区块未加载时高度图会返回世界底部，必须先强制加载才能拿到真实地表
        level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
        int top = level.getMaxBuildHeight() - 3;
        if (level.dimension() == Level.NETHER) {
            // 不要把玩家送到基岩顶上，把搜索限制在下界正常游玩高度内
            top = Math.min(top, 124);
        }
        int surface = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), top);
        int bottom = Math.max(level.getMinBuildHeight() + 1, surface - 48);
        for (int y = surface; y >= bottom; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (isSafeStandingSpot(level, feet)) {
                return Optional.of(feet);
            }
        }
        return Optional.empty();
    }

    /** 以出生点为中心、由近到远扩大范围搜索陆地落点；找不到时返回 empty */
    private static Optional<BlockPos> findSafeNear(ServerLevel level, BlockPos center) {
        Optional<BlockPos> centerSpot = findSafeColumn(level, center.getX(), center.getZ());
        if (centerSpot.isPresent()) {
            return centerSpot;
        }
        // 前几轮密一些，后几轮稀疏一些，兼顾命中率与区块生成开销
        int[][] passes = { { 8, 1 }, { 16, 2 }, { 32, 2 }, { 48, 4 } };
        for (int[] pass : passes) {
            int maxRadius = pass[0];
            int step = pass[1];
            for (int radius = step; radius <= maxRadius; radius += step) {
                for (int dx = -radius; dx <= radius; dx += step) {
                    for (int dz = -radius; dz <= radius; dz += step) {
                        // 只检查当前层的外边框，避免重复搜索
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        Optional<BlockPos> found = findSafeColumn(level, center.getX() + dx, center.getZ() + dz);
                        if (found.isPresent()) {
                            return found;
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** 找不到安全落点时提示玩家，传送保持取消状态 */
    private static void notifyNoLanding(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.endbound_ember.time_halo.no_safe_landing"), true);
    }

    /**
     * 在时间乱流维度里放置唯一的时之锚结构。
     * <p>
     * 只对该维度生效，并且用存档标记保证每个乱流维度最多生成一座；
     * 已经放置过（或者玩家自己拆掉了）就不会再补。
     */
    public static void ensureChronoAnchor(ServerLevel level) {
        if (!isTimeTurbulence(level)) {
            return;
        }
        ChronoAnchorSavedData data = ChronoAnchorSavedData.get(level);
        if (data.isPlaced()) {
            return;
        }
        StructureTemplate template = loadChronoAnchor(level);
        if (template == null) {
            return;
        }
        Vec3i size = template.getSize();
        for (int[] offset : CHRONO_ANCHOR_OFFSETS) {
            BlockPos origin = new BlockPos(
                    (int) Math.floor(SPAWN_X) + offset[0],
                    GROUND_Y + 1 + CHRONO_ANCHOR_Y_OFFSET,
                    (int) Math.floor(SPAWN_Z) + offset[1]);
            // 结构横跨多个区块，先把整片区域加载好，避免放置到一半区块还没就绪
            BlockPos far = origin.offset(size.getX() - 1, 0, size.getZ() - 1);
            for (int chunkX = origin.getX() >> 4; chunkX <= far.getX() >> 4; chunkX++) {
                for (int chunkZ = origin.getZ() >> 4; chunkZ <= far.getZ() >> 4; chunkZ++) {
                    level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
                }
            }
            // 玩家可能已经在出生点附近搭过东西，换个空位，绝不覆盖已有建筑
            if (!isChronoAnchorAreaClear(level, origin, size)) {
                continue;
            }
            // 结构文件里带着保存用的结构方块，包围盒里还有大片空白格：
            // 忽略空气与结构方块，既不会在平台上留下结构方块，也不会把周围地面挖出坑
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setKnownShape(true)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
            if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2)) {
                EndboundEmber.LOGGER.error("[时间乱流] 时之锚结构放置失败，位置 {}", origin);
                return;
            }
            data.markPlaced();
            EndboundEmber.LOGGER.info("[时间乱流] 时之锚已生成：{}，尺寸 {}", origin, size);
            return;
        }
        EndboundEmber.LOGGER.warn("[时间乱流] 时之锚的候选落点都已被占用，本次不生成");
    }

    /** 时之锚要占据的上方空间必须干净，避免把玩家已经搭好的建筑盖掉 */
    private static boolean isChronoAnchorAreaClear(ServerLevel level, BlockPos origin, Vec3i size) {
        BlockPos from = new BlockPos(origin.getX(), GROUND_Y + 1, origin.getZ());
        BlockPos to = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (!level.getBlockState(pos).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** 优先走结构管理器，找不到时按 data/<ns>/structures/ 手动读模板 */
    @Nullable
    private static StructureTemplate loadChronoAnchor(ServerLevel level) {
        StructureTemplate template = level.getStructureManager().get(CHRONO_ANCHOR).orElse(null);
        if (template != null) {
            return template;
        }
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(CHRONO_ANCHOR_RESOURCE);
        if (resource.isEmpty()) {
            EndboundEmber.LOGGER.error("[时间乱流] 时之锚结构模板不存在：{}", CHRONO_ANCHOR_RESOURCE);
            return null;
        }
        try (InputStream in = resource.get().open()) {
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            template = new StructureTemplate();
            template.load(level.holderLookup(Registries.BLOCK), tag);
            return template;
        } catch (Throwable e) {
            EndboundEmber.LOGGER.error("[时间乱流] 时之锚结构模板读取失败", e);
            return null;
        }
    }

    /** 取得（必要时创建）该玩家的时间乱流维度 */
    public static ServerLevel getOrCreate(MinecraftServer server, UUID playerId) {
        ResourceKey<Level> key = keyFor(playerId);
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            ensureChronoAnchor(existing);
            return existing;
        }

        Holder<DimensionType> type = server.registryAccess()
                .registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(DIMENSION_TYPE);
        Holder<Biome> biome = server.registryAccess()
                .registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);

        // 结构集显式给空：Optional.empty() 会让 FlatLevelSource 退回注册表里的全部结构集，
        // 从而在这个维度里刷出村庄等结构
        FlatLevelGeneratorSettings settings = new FlatLevelGeneratorSettings(
                Optional.of(HolderSet.<StructureSet>empty()), biome, List.of());
        settings.getLayersInfo().add(new FlatLayerInfo(BEDROCK_LAYERS, Blocks.BEDROCK));
        settings.getLayersInfo().add(new FlatLayerInfo(SMOOTH_STONE_LAYERS, Blocks.SMOOTH_STONE));
        settings.getLayersInfo().add(new FlatLayerInfo(1, Blocks.QUARTZ_BLOCK));
        settings.updateLayers();
        LevelStem stem = new LevelStem(type, new FlatLevelSource(settings));

        MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;
        ServerLevel overworld = server.overworld();
        ServerLevelData overworldData = (ServerLevelData) overworld.getLevelData();
        ServerLevel level = new ServerLevel(
                server,
                accessor.endbound$getExecutor(),
                accessor.endbound$getStorageSource(),
                new DerivedLevelData(server.getWorldData(), overworldData),
                key,
                stem,
                NOOP_LISTENER,
                false,
                BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed()),
                List.of(),
                false,
                null);

        overworld.getWorldBorder()
                .addListener(new net.minecraft.world.level.border.BorderChangeListener.DelegateBorderChangeListener(
                        level.getWorldBorder()));
        server.forgeGetWorldMap().put(key, level);
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        ensureChronoAnchor(level);
        EndboundEmber.LOGGER.info("[时间乱流] 已为玩家 {} 创建专属维度 {}", playerId, key.location());
        return level;
    }

    private static void teleport(ServerPlayer player, ServerLevel level,
            double x, double y, double z, float yRot, float xRot) {
        // 先强制生成落点区块，避免落地瞬间下方区块尚未就绪而直接坠落
        level.getChunk(BlockPos.containing(x, y, z).getX() >> 4,
                BlockPos.containing(x, y, z).getZ() >> 4, ChunkStatus.FULL, true);
        player.stopRiding();
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(level, x, y, z, yRot, xRot);
    }

    private static void saveReturnPoint(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putString(RETURN_DIMENSION, player.level().dimension().location().toString());
        data.putDouble(RETURN_X, player.getX());
        data.putDouble(RETURN_Y, player.getY());
        data.putDouble(RETURN_Z, player.getZ());
        data.putFloat(RETURN_YAW, player.getYRot());
        data.putFloat(RETURN_PITCH, player.getXRot());
    }

    /**
     * 记录玩家自己站在乱流维度里的位置，重进时直接回到自己的庇护所。
     * <p>只有维度主人在自己的维度里才记录；作客的玩家不写入，避免覆盖他家的坐标。
     */
    public static void saveOwnPosition(ServerPlayer player) {
        if (!ownerOf(player.level()).map(uuid -> uuid.equals(player.getUUID())).orElse(false)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        data.putDouble(LAST_X, player.getX());
        data.putDouble(LAST_Y, player.getY());
        data.putDouble(LAST_Z, player.getZ());
        data.putFloat(LAST_YAW, player.getYRot());
        data.putFloat(LAST_PITCH, player.getXRot());
    }

    private static void clearReturnPoint(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(RETURN_DIMENSION);
        data.remove(RETURN_X);
        data.remove(RETURN_Y);
        data.remove(RETURN_Z);
        data.remove(RETURN_YAW);
        data.remove(RETURN_PITCH);
    }
}
