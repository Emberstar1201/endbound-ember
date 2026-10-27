package com.emberstar.endbounember.block;

import com.mojang.serialization.MapCodec;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 展示台：台面中心可放置任意物品并悬浮旋转展示，右键可取回。
 * <p>
 * 当周围的展示台摆成祭坛形状、且台面上放着星尘手绳时，右键中间的展示台
 * 会消耗 12 万点经验值，播放 15 秒的仪式动画，最后把手绳替换为星尘光环。
 */
public class DisplayPedestalBlock extends BaseEntityBlock {
    public static final MapCodec<DisplayPedestalBlock> CODEC = simpleCodec(DisplayPedestalBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** 碰撞箱：底座与台面均为整格宽，高度到台面顶部 16 像素 */
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);

    /** 祭坛环绕半径：中心展示台四周 3 格范围内的展示台 */
    private static final int ALTAR_RADIUS = 3;

    /** 悬浮动画的生效距离：玩家在此范围内才推进动画，超出后动画冻结 */
    private static final double ANIMATION_RANGE = 48.0D;

    /** 汇聚粒子的星尘颜色（淡紫） */
    private static final Vector3f HALO_DUST_COLOR = new Vector3f(0.78F, 0.72F, 1.0F);
    /** 一个粒子从环绕展示台飞到中心所需的 tick 数 */
    private static final int PARTICLE_TRAVEL_TICKS = 40;
    /** 每个环绕展示台同时存在的粒子数，越多轨迹越连贯但开销越大 */
    private static final int PARTICLES_PER_SOURCE = 4;

    public DisplayPedestalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayPedestalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, EndboundEmber.DISPLAY_PEDESTAL_BLOCK_ENTITY.get(),
                level.isClientSide ? DisplayPedestalBlock::clientTick : DisplayPedestalBlock::serverTick);
    }

    /**
     * 客户端推进悬浮动画。只有玩家进入 {@link #ANIMATION_RANGE} 格内才推进，
     * 玩家远离后动画停在原地，回来时从停住的位置继续，避免远处展示台持续消耗渲染开销。
     */
    private static void clientTick(Level level, BlockPos pos, BlockState state,
            DisplayPedestalBlockEntity pedestal) {
        if (level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                ANIMATION_RANGE, false) != null) {
            pedestal.advanceAnimation();
        }
    }

    /** 服务端推进仪式动画：汇聚粒子、到时间后把手绳替换成星尘光环、最后炸开 */
    private static void serverTick(Level level, BlockPos pos, BlockState state, DisplayPedestalBlockEntity pedestal) {
        if (!pedestal.isRitualActive()) {
            return;
        }
        long elapsed = level.getGameTime() - pedestal.getRitualStartTime();
        if (level instanceof ServerLevel serverLevel) {
            spawnConvergingParticles(serverLevel, pos, pedestal, elapsed);
        }
        if (elapsed >= EndboundEmber.HALO_RITUAL_SWAP_TICK && !pedestal.isRitualSwapped()) {
            pedestal.setStoredItem(new ItemStack(EndboundEmber.STARDUST_WITCH_HALO.get()));
            pedestal.setRitualSwapped(true);
            if (level instanceof ServerLevel serverLevel) {
                spawnBurstParticles(serverLevel, pos);
                serverLevel.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.2F);
            }
        }
        if (elapsed >= EndboundEmber.HALO_RITUAL_DURATION) {
            pedestal.stopRitual();
        }
    }

    /** 汇聚粒子：从每个环绕展示台上方沿拱形曲线流向中心 */
    private static void spawnConvergingParticles(ServerLevel level, BlockPos center,
            DisplayPedestalBlockEntity pedestal, long elapsed) {
        List<BlockPos> sources = pedestal.getAltarPedestals();
        if (sources.isEmpty()) {
            return;
        }
        double cx = center.getX() + 0.5D;
        double cy = center.getY() + 1.35D;
        double cz = center.getZ() + 0.5D;
        for (int i = 0; i < sources.size(); i++) {
            BlockPos source = sources.get(i);
            double sx = source.getX() + 0.5D;
            double sy = source.getY() + 1.45D;
            double sz = source.getZ() + 0.5D;
            // 控制点抬到两端之上，让轨迹呈拱形而不是直线
            double px = (sx + cx) * 0.5D;
            double py = Math.max(sy, cy) + 1.4D;
            double pz = (sz + cz) * 0.5D;
            // 每个源点错开相位，形成连续不断的星尘流
            for (int k = 0; k < PARTICLES_PER_SOURCE; k++) {
                int phase = (int) ((elapsed + i * 5L + k * (PARTICLE_TRAVEL_TICKS / PARTICLES_PER_SOURCE))
                        % PARTICLE_TRAVEL_TICKS);
                double t = phase / (double) PARTICLE_TRAVEL_TICKS;
                double inv = 1.0D - t;
                double x = inv * inv * sx + 2.0D * inv * t * px + t * t * cx;
                double y = inv * inv * sy + 2.0D * inv * t * py + t * t * cy;
                double z = inv * inv * sz + 2.0D * inv * t * pz + t * t * cz;
                level.sendParticles(new DustParticleOptions(HALO_DUST_COLOR, 0.9F),
                        x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (k == 0) {
                    level.sendParticles(ParticleTypes.END_ROD,
                            x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    /** 完成瞬间的烟花式爆发：闪光、爆炸环、四散的星尘与火花 */
    private static void spawnBurstParticles(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 1.35D;
        double cz = pos.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FIREWORK, cx, cy, cz, 120, 0.15D, 0.15D, 0.15D, 0.45D);
        level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 80, 0.2D, 0.2D, 0.2D, 0.35D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 60, 0.3D, 0.3D, 0.3D, 0.5D);
        level.sendParticles(new DustParticleOptions(HALO_DUST_COLOR, 1.8F),
                cx, cy, cz, 90, 0.4D, 0.4D, 0.4D, 0.25D);
    }

    /** 手持物品右键：空台放入物品；台面是手绳时尝试仪式；台面是祭坛材料时拦截；其余取回 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof DisplayPedestalBlockEntity pedestal)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (pedestal.isRitualActive()) {
            return ItemInteractionResult.SUCCESS;
        }
        ItemStack stored = pedestal.getStoredItem();
        if (stored.isEmpty()) {
            if (stack.isEmpty()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            ItemStack toStore = stack.copyWithCount(1);
            pedestal.setStoredItem(toStore);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
            return ItemInteractionResult.SUCCESS;
        }
        // 台面放着星尘手绳：无论手持什么都只尝试启动仪式，手绳无法直接取回
        if (stored.is(EndboundEmber.STARDUST_CORD_BRACELET.get())) {
            tryStartRitual(level, pos, player, pedestal);
            return ItemInteractionResult.SUCCESS;
        }
        // 环绕展示台上的虚无星尘是仪式材料，不允许取回
        if (findAltarCenter(level, pos) != null) {
            return ItemInteractionResult.SUCCESS;
        }
        // 已有物品时右键取回
        takeBack(level, pos, player, pedestal);
        return ItemInteractionResult.SUCCESS;
    }

    /** 空手右键：台面是手绳时尝试仪式；台面是祭坛材料时拦截取回；其余情况取回物品 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof DisplayPedestalBlockEntity pedestal) || pedestal.getStoredItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // 仪式进行中不允许取回任何物品
        if (pedestal.isRitualActive()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stored = pedestal.getStoredItem();
        // 台面放着星尘手绳：尝试启动仪式，手绳无法直接取回
        if (stored.is(EndboundEmber.STARDUST_CORD_BRACELET.get())) {
            tryStartRitual(level, pos, player, pedestal);
            return InteractionResult.SUCCESS;
        }
        // 环绕展示台上的虚无星尘是仪式材料，取回同样拦截
        if (findAltarCenter(level, pos) != null) {
            return InteractionResult.SUCCESS;
        }
        takeBack(level, pos, player, pedestal);
        return InteractionResult.SUCCESS;
    }

    /** 若该展示台属于某个完整祭坛的环绕位，返回祭坛中心；否则返回 null */
    @Nullable
    private static BlockPos findAltarCenter(Level level, BlockPos pos) {
        for (BlockPos center : BlockPos.betweenClosed(
                pos.offset(-ALTAR_RADIUS, 0, -ALTAR_RADIUS), pos.offset(ALTAR_RADIUS, 0, ALTAR_RADIUS))) {
            if (center.equals(pos)) {
                continue;
            }
            if (scanAltar(level, center) != null) {
                return center.immutable();
            }
        }
        return null;
    }

    /**
     * 校验展示台层与末地石底座层。展示台必须位于同一层，底座必须位于展示台正下方一层。
     */
    @Nullable
    private static List<BlockPos> scanAltar(Level level, BlockPos center) {
        int radius = EndboundEmber.ALTAR_BASE_SIZE / 2;
        BlockPos baseCenter = center.below();

        // 7×7 底座必须全部是末地石；中心位置由中心展示台覆盖，不影响底座检查。
        for (BlockPos base : BlockPos.betweenClosed(
                baseCenter.offset(-radius, 0, -radius), baseCenter.offset(radius, 0, radius))) {
            if (!level.getBlockState(base).is(Blocks.END_STONE)) {
                return null;
            }
        }

        List<BlockPos> found = new ArrayList<>();
        for (BlockPos candidate : BlockPos.betweenClosed(
                center.offset(-radius, 0, -radius), center.offset(radius, 0, radius))) {
            if (candidate.equals(center)) {
                continue;
            }
            BlockState state = level.getBlockState(candidate);
            if (!state.is(EndboundEmber.DISPLAY_PEDESTAL.get())) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(candidate);
            if (!(blockEntity instanceof DisplayPedestalBlockEntity pedestal)
                    || !pedestal.getStoredItem().is(EndboundEmber.VOID_EMBERSTAR.get())) {
                return null;
            }
            found.add(candidate.immutable());
        }
        return found.size() == EndboundEmber.HALO_RITUAL_PEDESTAL_COUNT ? found : null;
    }

    /** 尝试启动仪式：祭坛结构完整时扣除经验并开始，否则在动作栏提示结构不完整 */
    private static void tryStartRitual(Level level, BlockPos pos, Player player,
            DisplayPedestalBlockEntity pedestal) {
        List<BlockPos> altar = scanAltar(level, pos);
        if (altar == null) {
            player.displayClientMessage(
                    Component.translatable("block.endbound_ember.display_pedestal.altar_incomplete"), true);
            return;
        }
        startRitual(level, pos, player, pedestal, altar);
    }

    /** 扣除经验值并启动 15 秒仪式 */
    private static void startRitual(Level level, BlockPos pos, Player player, DisplayPedestalBlockEntity pedestal,
            List<BlockPos> altar) {
        if (!player.getAbilities().instabuild) {
            if (player.experienceLevel < 30) {
                player.displayClientMessage(
                        Component.translatable("block.endbound_ember.display_pedestal.not_enough_experience"), true);
                return;
            }
            player.giveExperiencePoints(-EndboundEmber.HALO_RITUAL_EXPERIENCE_COST);
        }
        pedestal.startRitual(level.getGameTime(), altar);
        level.playSound(null, pos, EndboundEmber.HALO_RITUAL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D,
                    60, 1.2D, 0.4D, 1.2D, 0.05D);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            var server = serverPlayer.getServer();
            if (server != null) {
                var advancement = server.getAdvancements().get(EndboundEmber.STARDUST_BLESSING_ADVANCEMENT);
                if (advancement != null) {
                    serverPlayer.getAdvancements().award(advancement, EndboundEmber.STARDUST_BLESSING_CRITERION);
                }
            }
        }
    }

    private static void takeBack(Level level, BlockPos pos, Player player, DisplayPedestalBlockEntity pedestal) {
        ItemStack stored = pedestal.getStoredItem();
        if (stored.isEmpty()) {
            return;
        }
        pedestal.setStoredItem(ItemStack.EMPTY);
        if (!player.getInventory().add(stored)) {
            player.drop(stored, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    /** 方块被破坏时把展示中的物品掉落出来，避免物品丢失 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof DisplayPedestalBlockEntity pedestal) {
                ItemStack stored = pedestal.getStoredItem();
                if (!stored.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, stored);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
