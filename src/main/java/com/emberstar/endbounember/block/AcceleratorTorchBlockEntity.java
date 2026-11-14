package com.emberstar.endbounember.block;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 加速火把的方块实体。
 * <p>自身每 tick 扫描半径内的方块并补做随机刻；同时把所在位置登记到静态表里，
 * 供 {@link com.emberstar.endbounember.mixin.LevelTimeStopBlockEntityMixin}
 * 在方块实体 tick 时查询是否需要额外多跑几次。</p>
 */
public class AcceleratorTorchBlockEntity extends BlockEntity {

    /** 每个世界维度当前加载中的加速火把位置 */
    private static final Map<Level, Set<BlockPos>> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** 上一次执行随机刻扫描的世界时间，防止火把把自己也加速后重复扫描 */
    private long lastRandomTickPass = Long.MIN_VALUE;

    public AcceleratorTorchBlockEntity(BlockPos pos, BlockState state) {
        super(EndboundEmber.ACCELERATOR_TORCH_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null) {
            synchronized (ACTIVE) {
                ACTIVE.computeIfAbsent(this.level, key -> new HashSet<>()).add(this.worldPosition);
            }
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null) {
            synchronized (ACTIVE) {
                Set<BlockPos> positions = ACTIVE.get(this.level);
                if (positions != null) {
                    positions.remove(this.worldPosition);
                    if (positions.isEmpty()) {
                        ACTIVE.remove(this.level);
                    }
                }
            }
        }
        super.setRemoved();
    }

    /** 该位置是否处在某个加速火把的范围内；是则返回需要额外补跑的 tick 次数 */
    public static int extraTicksAt(Level level, BlockPos pos) {
        if (!Config.ACCELERATOR_TORCH_ENABLED.get()) {
            return 0;
        }
        int speed = Config.ACCELERATOR_TORCH_SPEED.get();
        if (speed <= 1) {
            return 0;
        }
        int radius = Config.ACCELERATOR_TORCH_RADIUS.get();
        double limit = (double) radius * radius;
        synchronized (ACTIVE) {
            Set<BlockPos> positions = ACTIVE.get(level);
            if (positions == null) {
                return 0;
            }
            for (BlockPos torch : positions) {
                if (torch.distSqr(pos) <= limit) {
                    return speed - 1;
                }
            }
        }
        return 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AcceleratorTorchBlockEntity entity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!Config.ACCELERATOR_TORCH_ENABLED.get()) {
            return;
        }
        int speed = Config.ACCELERATOR_TORCH_SPEED.get();
        if (speed <= 1) {
            return;
        }
        int radius = Config.ACCELERATOR_TORCH_RADIUS.get();
        // 火把自身也在加速范围内，会被额外 tick；同一世界 tick 只补一次随机刻
        long gameTime = serverLevel.getGameTime();
        if (entity.lastRandomTickPass == gameTime) {
            return;
        }
        entity.lastRandomTickPass = gameTime;
        RandomSource random = serverLevel.random;
        BlockPos min = pos.offset(-radius, -radius, -radius);
        BlockPos max = pos.offset(radius, radius, radius);
        // 原版每 tick 已经随机刻过一次，这里只补 speed - 1 次，使范围内达到 speed 倍速度
        for (int pass = 1; pass < speed; pass++) {
            for (BlockPos target : BlockPos.betweenClosed(min, max)) {
                BlockState targetState = serverLevel.getBlockState(target);
                if (targetState.isRandomlyTicking()) {
                    targetState.randomTick(serverLevel, target, random);
                }
            }
        }
    }
}
