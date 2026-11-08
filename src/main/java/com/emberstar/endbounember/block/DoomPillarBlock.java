package com.emberstar.endbounember.block;

import com.emberstar.endbounember.EndboundEmber;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 亡者之柱方块：外形是一根三格高的黑曜石柱，无法被挖掘破坏，
 * 只能通过左键攻击削减柱子血量（见 {@link DoomPillarBlockEntity}）。
 *
 * <p>柱子由底座、柱身、顶端三格方块拼成，只有底座挂方块实体。
 * 之所以拆成真实的三格而不是在底座上用一个三格高的碰撞箱：原版射线检测
 * （{@code BlockGetter.traverseBlocks}）是逐格遍历的，只会取射线经过的那一格
 * 方块的形状，底座的三格高形状在瞄准柱身中上部时根本不会被检测到。</p>
 */
public class DoomPillarBlock extends BaseEntityBlock {
    public static final MapCodec<DoomPillarBlock> CODEC = simpleCodec(DoomPillarBlock::new);

    /** 柱子总高（格） */
    public static final int HEIGHT = 3;

    /** 柱子分段：只有 {@link Part#BASE} 携带方块实体与血条 */
    public enum Part implements StringRepresentable {
        BASE("base"),
        MIDDLE("middle"),
        TOP("top");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /** 底座的石台，比柱身略宽 */
    private static final VoxelShape BASE_SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
    /** 柱身比外观略宽一点，方便玩家命中 */
    private static final VoxelShape MIDDLE_SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);
    /** 顶端的水晶段 */
    private static final VoxelShape TOP_SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 16.0D, 13.0D);

    public DoomPillarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    /** 在 base 处放置一根完整的三格柱子 */
    public static void placePillar(Level level, BlockPos base) {
        BlockState state = EndboundEmber.DOOM_PILLAR.get().defaultBlockState();
        level.setBlockAndUpdate(base, state.setValue(PART, Part.BASE));
        level.setBlockAndUpdate(base.above(), state.setValue(PART, Part.MIDDLE));
        level.setBlockAndUpdate(base.above(2), state.setValue(PART, Part.TOP));
    }

    /** 移除整根柱子：从上往下拆，底座最后移除以便正常触发方块实体的清理 */
    public static void removePillar(Level level, BlockPos base) {
        Block block = EndboundEmber.DOOM_PILLAR.get();
        for (int dy = HEIGHT - 1; dy >= 0; dy--) {
            BlockPos pos = base.above(dy);
            if (level.getBlockState(pos).is(block)) {
                level.destroyBlock(pos, false);
            }
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 底座消失时把柱身与顶端一并清掉，避免留下打不掉的残段
        if (state.getValue(PART) == Part.BASE && !state.is(newState.getBlock())) {
            for (int dy = 1; dy < HEIGHT; dy++) {
                BlockPos above = pos.above(dy);
                if (level.getBlockState(above).is(this)) {
                    level.destroyBlock(above, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case MIDDLE -> MIDDLE_SHAPE;
            case TOP -> TOP_SHAPE;
            case BASE -> BASE_SHAPE;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // 只有底座携带血量与血条；柱身、顶端两格纯粹用来承接射线检测
        return state.getValue(PART) == Part.BASE ? new DoomPillarBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, EndboundEmber.DOOM_PILLAR_BLOCK_ENTITY.get(),
                DoomPillarBlockEntity::serverTick);
    }

    /** 左键攻击柱子：伤害走柱子自己的血量，而不是原版挖掘 */
    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static final class Events {
        private Events() {
        }

        @SubscribeEvent
        public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
            Level level = event.getLevel();
            BlockPos base = resolveBase(level, event.getPos());
            if (base == null) {
                return;
            }
            // 客户端照常发包，交给服务端统一结算，避免两边重复扣血
            if (level.isClientSide) {
                return;
            }
            event.setCanceled(true);
            if (level.getBlockEntity(base) instanceof DoomPillarBlockEntity pillar) {
                pillar.hit(event.getEntity());
            }
        }

        /** 柱子占三格，点中柱身、顶端任意一段都回溯到底座的方块实体 */
        @Nullable
        private static BlockPos resolveBase(BlockGetter level, BlockPos pos) {
            for (int dy = 0; dy < HEIGHT; dy++) {
                BlockPos candidate = pos.below(dy);
                if (level.getBlockEntity(candidate) instanceof DoomPillarBlockEntity) {
                    return candidate;
                }
            }
            return null;
        }

        @SubscribeEvent
        public static void onBreak(BlockEvent.BreakEvent event) {
            if (event.getState().is(EndboundEmber.DOOM_PILLAR.get())) {
                event.setCanceled(true);
            }
        }
    }
}
