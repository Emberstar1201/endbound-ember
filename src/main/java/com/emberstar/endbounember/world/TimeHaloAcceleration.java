package com.emberstar.endbounember.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 时光光环的「附近时间加速」加成：佩戴者附近的作物、酿造台、
 * 熔炉 / 烟熏炉 / 高炉各自获得额外 250% 的时间流速（约 3.5 倍）。
 *
 * <p>为了控制开销，附近方块每 10 tick 扫描一次并缓存位置，
 * 每 tick 只对缓存中的方块补跑额外的生长 / 处理逻辑。</p>
 */
public final class TimeHaloAcceleration {

    /** 扫描的水平 / 垂直半径（格） */
    private static final int HORIZONTAL_RADIUS = 12;
    private static final int VERTICAL_RADIUS = 8;
    /** 位置缓存的重扫间隔（tick） */
    private static final int SCAN_INTERVAL = 10;
    /** 每 tick 额外补跑的次数：2 与 3 交替，平均多 2.5 次即为 +250% */
    private static final int EXTRA_LOW = 2;
    private static final int EXTRA_HIGH = 3;

    private static final Map<UUID, List<BlockPos>> TRACKED = new HashMap<>();

    private TimeHaloAcceleration() {
    }

    /** 由技能 tick 调用；玩家未佩戴光环时不会进入这里 */
    public static void tickPlayer(ServerPlayer player) {
        UUID id = player.getUUID();
        if (player.tickCount % SCAN_INTERVAL == 0 || TRACKED.get(id) == null) {
            TRACKED.put(id, scan(player));
        }
        List<BlockPos> positions = TRACKED.get(id);
        if (positions == null || positions.isEmpty()) return;
        accelerate(player, positions);
    }

    /** 忘记该玩家的缓存（摘下光环或下线时调用） */
    public static void clear(ServerPlayer player) {
        TRACKED.remove(player.getUUID());
    }

    /** 扫描附近值得加速的方块位置 */
    private static List<BlockPos> scan(ServerPlayer player) {
        List<BlockPos> found = new ArrayList<>();
        if (!(player.level() instanceof ServerLevel level)) return found;
        BlockPos center = player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
            for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    if (isAcceleratable(state)) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        return found;
    }

    /** 对缓存位置补跑额外的时间流速 */
    private static void accelerate(ServerPlayer player, List<BlockPos> positions) {
        if (!(player.level() instanceof ServerLevel level)) return;
        int extra = player.tickCount % 2 == 0 ? EXTRA_HIGH : EXTRA_LOW;
        // 作物靠随机刻生长：额外 250% 即把随机刻概率提高到原来的 2.5 倍
        // （原版每 tick 每方块中随机刻的概率约为 randomTickSpeed / 4096）
        int randomTickSpeed = level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        float extraCropChance = 2.5F * randomTickSpeed / 4096.0F;
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (isCrop(state)) {
                if (level.random.nextFloat() < extraCropChance) {
                    state.randomTick(level, pos, level.random);
                }
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof AbstractFurnaceBlockEntity furnace
                    && state.getBlock() instanceof AbstractFurnaceBlock) {
                for (int i = 0; i < extra; i++) {
                    AbstractFurnaceBlockEntity.serverTick(level, pos, state, furnace);
                }
            } else if (blockEntity instanceof BrewingStandBlockEntity brewing
                    && state.getBlock() instanceof BrewingStandBlock) {
                for (int i = 0; i < extra; i++) {
                    BrewingStandBlockEntity.serverTick(level, pos, state, brewing);
                }
            }
        }
    }

    private static boolean isAcceleratable(BlockState state) {
        Block block = state.getBlock();
        return isCrop(state)
                || block instanceof AbstractFurnaceBlock
                || block instanceof BrewingStandBlock;
    }

    private static boolean isCrop(BlockState state) {
        Block block = state.getBlock();
        return block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof NetherWartBlock
                || block instanceof CocoaBlock
                || block instanceof SweetBerryBushBlock;
    }
}
