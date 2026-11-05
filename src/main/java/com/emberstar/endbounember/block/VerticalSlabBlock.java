package com.emberstar.endbounember.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 竖半砖：高度为一格、宽度为半格的非完整方块，可朝东南西北四个方向放置。
 * 相比原版横向半砖，竖半砖适合做立柱、薄墙与装饰边框。
 *
 * <p>放置逻辑：点击某个方块的面时，竖半砖优先贴在该方块一侧；
 * 点击上/下表面时，根据点击位置选择占据的半边。
 * 对同一方块位置再次放置互补的半片时，合并为完整方块（double 状态），行为参考原版横半砖。</p>
 */
public class VerticalSlabBlock extends Block {
    public static final MapCodec<VerticalSlabBlock> CODEC = simpleCodec(VerticalSlabBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** 是否为两片合并的完整方块状态 */
    public static final BooleanProperty DOUBLE = BooleanProperty.create("double");

    /** 北半侧：z 从 0 到 8 */
    private static final VoxelShape NORTH_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 8.0D);
    /** 南半侧：z 从 8 到 16 */
    private static final VoxelShape SOUTH_SHAPE = Block.box(0.0D, 0.0D, 8.0D, 16.0D, 16.0D, 16.0D);
    /** 西半侧：x 从 0 到 8 */
    private static final VoxelShape WEST_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 8.0D, 16.0D, 16.0D);
    /** 东半侧：x 从 8 到 16 */
    private static final VoxelShape EAST_SHAPE = Block.box(8.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    public VerticalSlabBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DOUBLE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DOUBLE);
    }

    /**
     * 放置状态判定：
     * 1. 点击位置已有同款单片竖半砖时，合并为完整方块；
     * 2. 点击竖直侧面时，半砖贴在被点击方块一侧；
     * 3. 点击上/下表面时，按点击位置距中心更远的水平轴选择半边。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState state = context.getLevel().getBlockState(pos);
        if (state.is(this) && !state.getValue(DOUBLE)) {
            return state.setValue(DOUBLE, true);
        }
        if (state.getBlock() instanceof VerticalSlabBlock && !state.getValue(DOUBLE)) {
            // 异类材质的合并由 VerticalSlabItem 接管，此处返回 null 阻止普通放置流程
            return null;
        }
        Direction face = context.getClickedFace();
        Direction facing;
        if (face.getAxis().isHorizontal()) {
            facing = face.getOpposite();
        } else {
            double offsetX = context.getClickLocation().x - pos.getX();
            double offsetZ = context.getClickLocation().z - pos.getZ();
            if (Math.abs(offsetX - 0.5D) >= Math.abs(offsetZ - 0.5D)) {
                facing = offsetX < 0.5D ? Direction.WEST : Direction.EAST;
            } else {
                facing = offsetZ < 0.5D ? Direction.NORTH : Direction.SOUTH;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(DOUBLE, false);
    }

    /**
     * 允许对同款单片竖半砖的空白半边继续放置，逻辑与原版横半砖一致：
     * 点击空白侧的内侧面，或在垂直面上点击空白半边时合并；点击平整外侧面则不占用原位置。
     */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (state.getValue(DOUBLE)) {
            return false;
        }
        // 允许任何竖半砖物品对本方块的空白半边继续放置；异类材质由物品侧合并为混合方块
        if (!(context.getItemInHand().getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof VerticalSlabBlock)) {
            return false;
        }
        if (!context.replacingClickedOnBlock()) {
            return true;
        }
        Direction facing = state.getValue(FACING);
        Direction face = context.getClickedFace();
        if (face == facing.getOpposite()) {
            return true;
        }
        if (face.getAxis() != facing.getAxis()) {
            double fraction = facing.getAxis() == Direction.Axis.X
                    ? context.getClickLocation().x - context.getClickedPos().getX()
                    : context.getClickLocation().z - context.getClickedPos().getZ();
            return facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? fraction < 0.5D
                    : fraction > 0.5D;
        }
        return false;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** 根据朝向返回对应的半格碰撞箱；两片合并时占满整格 */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(DOUBLE)) {
            return Shapes.block();
        }
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            case EAST -> EAST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }
}
