package com.emberstar.endbounember.block;

import com.emberstar.endbounember.EndboundEmber;
import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 加速火把：外形是一根插在地面上的火把，持续加速半径内方块的随机刻与方块实体。
 * <p>具体倍率与半径见 {@link com.emberstar.endbounember.Config} 中的加速火把配置项，
 * 加速逻辑本身在 {@link AcceleratorTorchBlockEntity} 与
 * {@link com.emberstar.endbounember.mixin.LevelTimeStopBlockEntityMixin} 中。</p>
 */
public class AcceleratorTorchBlock extends BaseEntityBlock {
    public static final MapCodec<AcceleratorTorchBlock> CODEC = simpleCodec(AcceleratorTorchBlock::new);

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            BlockStateProperties.FACING;
    private static final VoxelShape FLOOR_SHAPE = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 12.0D, 10.0D);
    private static final VoxelShape WALL_SHAPE_NORTH = Block.box(5.5D, 3.0D, 11.0D, 10.5D, 13.0D, 16.0D);
    private static final VoxelShape WALL_SHAPE_SOUTH = Block.box(5.5D, 3.0D, 0.0D, 10.5D, 13.0D, 5.0D);
    private static final VoxelShape WALL_SHAPE_WEST = Block.box(11.0D, 3.0D, 5.5D, 16.0D, 13.0D, 10.5D);
    private static final VoxelShape WALL_SHAPE_EAST = Block.box(0.0D, 3.0D, 5.5D, 5.0D, 13.0D, 10.5D);

    public AcceleratorTorchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> WALL_SHAPE_NORTH;
            case SOUTH -> WALL_SHAPE_SOUTH;
            case WEST -> WALL_SHAPE_WEST;
            case EAST -> WALL_SHAPE_EAST;
            default -> FLOOR_SHAPE;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 地面状态检查下方支撑，墙面状态检查朝向反面的支撑。 */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        if (facing == Direction.DOWN) {
            return false;
        }
        Direction supportDirection = facing == Direction.UP ? Direction.DOWN : facing.getOpposite();
        BlockPos supportPos = pos.relative(supportDirection);
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, supportDirection.getOpposite());
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace == Direction.DOWN) {
            return null;
        }
        BlockState state = this.defaultBlockState().setValue(FACING, clickedFace);
        return this.canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        Direction supportDirection = facing == Direction.UP ? Direction.DOWN : facing.getOpposite();
        return direction == supportDirection && !this.canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.7D;
        double z = pos.getZ() + 0.5D;
        if (facing != Direction.UP) {
            // 与原版墙上火把一致：火焰尖端偏向墙面一侧，位置更高
            Direction support = facing.getOpposite();
            x += 0.27D * support.getStepX();
            z += 0.27D * support.getStepZ();
            y += 0.22D;
        }
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
        level.addParticle(EndboundEmber.ACCELERATOR_FLAME.get(), x, y, z, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AcceleratorTorchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, EndboundEmber.ACCELERATOR_TORCH_BLOCK_ENTITY.get(),
                AcceleratorTorchBlockEntity::serverTick);
    }
}
