package com.emberstar.endbounember.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * 无缝玻璃：与同种无缝玻璃相邻时隐藏接触棱上的纹理边框，视觉上连成整片。
 * <p>
 * 六个布尔属性记录<b>该方向是否没有同种无缝玻璃</b>（true = 该面暴露在外）。
 * 方块状态模型只为暴露的棱绘制 1 像素边框，相邻处不绘制，于是接缝处的边框消失。
 * 注意属性语义与直觉相反：若改成「连接成立」，单独放置的玻璃会失去所有边框而完全看不见。
 */
public class SeamlessGlassBlock extends TransparentBlock {
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");

    private static final Map<Direction, BooleanProperty> EXPOSED_BY_DIRECTION = Map.of(
            Direction.UP, UP,
            Direction.DOWN, DOWN,
            Direction.NORTH, NORTH,
            Direction.SOUTH, SOUTH,
            Direction.EAST, EAST,
            Direction.WEST, WEST);

    /** 遮光玻璃：不透过天光、光衰为最大光照等级，与同种玻璃同样会相互连接 */
    private final boolean tinted;

    public SeamlessGlassBlock(BlockBehaviour.Properties properties, boolean tinted) {
        super(properties);
        this.tinted = tinted;
        // 默认六个方向都暴露，避免通过放置以外的方式（/setblock、结构）生成时玻璃完全透明
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(UP, true)
                .setValue(DOWN, true)
                .setValue(NORTH, true)
                .setValue(SOUTH, true)
                .setValue(EAST, true)
                .setValue(WEST, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(UP, DOWN, NORTH, SOUTH, EAST, WEST);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState();
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = context.getClickedPos().relative(direction);
            state = state.setValue(EXPOSED_BY_DIRECTION.get(direction),
                    !context.getLevel().getBlockState(neighbourPos).is(this));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
            LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BooleanProperty exposed = EXPOSED_BY_DIRECTION.get(direction);
        boolean value = !neighbourState.is(this);
        if (state.getValue(exposed) != value) {
            return state.setValue(exposed, value);
        }
        return state;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !this.tinted;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return this.tinted ? level.getMaxLightLevel() : super.getLightBlock(state, level, pos);
    }
}
