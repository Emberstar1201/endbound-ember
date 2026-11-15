package com.emberstar.endbounember.block;

import com.mojang.serialization.MapCodec;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.Herobrine;
import com.emberstar.endbounember.entity.ModEntities;
import com.emberstar.endbounember.util.ExperienceUtil;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
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

    /** Herobrine 召唤法阵的外圈半径（格） */
    private static final double NECRO_RING_OUTER = 2.75D;
    /** Herobrine 召唤法阵的内圈半径（格） */
    private static final double NECRO_RING_INNER = 1.85D;
    /** 外圈 / 内圈一次绘制使用的魂火粒子数 */
    private static final int NECRO_RING_OUTER_POINTS = 14;
    private static final int NECRO_RING_INNER_POINTS = 10;

    /** 悬浮动画的生效距离：玩家在此范围内才推进动画，超出后动画冻结 */
    private static final double ANIMATION_RANGE = 48.0D;

    /** 汇聚粒子的星尘颜色（淡紫）：星尘光环仪式 */
    private static final Vector3f HALO_DUST_COLOR = new Vector3f(0.78F, 0.72F, 1.0F);
    /** 汇聚粒子的晨曦颜色（#fff5d7）：晨曦护符仪式 */
    private static final Vector3f AMULET_DUST_COLOR = new Vector3f(1.0F, 0.96F, 0.84F);
    /** 汇聚粒子的嫩绿颜色：生命之星仪式 */
    private static final Vector3f LIFE_STAR_DUST_COLOR = new Vector3f(0.58F, 0.95F, 0.52F);
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

    /** 服务端推进仪式动画：汇聚粒子、到时间后把材料替换成产物、最后炸开 */
    private static void serverTick(Level level, BlockPos pos, BlockState state, DisplayPedestalBlockEntity pedestal) {
        if (!pedestal.isRitualActive()) {
            return;
        }
        DisplayPedestalBlockEntity.RitualType type = pedestal.getRitualType();
        long elapsed = level.getGameTime() - pedestal.getRitualStartTime();
        if (level instanceof ServerLevel serverLevel) {
            if (type == DisplayPedestalBlockEntity.RitualType.HEROBRINE) {
                // 亡灵法阵一直铺在地上，四角下界之星的能量在召唤前持续流向中心
                spawnNecroCircle(serverLevel, pos, elapsed);
                if (elapsed < type.swapTick()) {
                    spawnSoulStreams(serverLevel, pos, pedestal, elapsed);
                    spawnSummonCharge(serverLevel, pos, elapsed, type.swapTick());
                }
            } else {
                spawnConvergingParticles(serverLevel, pos, pedestal, elapsed, dustColor(type));
            }
        }
        if (elapsed >= type.swapTick() && !pedestal.isRitualSwapped()) {
            pedestal.setRitualSwapped(true);
            if (type == DisplayPedestalBlockEntity.RitualType.HEROBRINE) {
                // 召唤：中心末影水晶与四角材料一并消耗，爆炸后 Herobrine 从法阵中心现身
                pedestal.setStoredItem(ItemStack.EMPTY);
                consumeAltarMaterials(level, pedestal);
                if (level instanceof ServerLevel serverLevel) {
                    spawnSummonBurst(serverLevel, pos);
                    summonHerobrine(serverLevel, pos);
                }
            } else {
                pedestal.setStoredItem(new ItemStack(product(type)));
                consumeAltarMaterials(level, pedestal);
                if (level instanceof ServerLevel serverLevel) {
                    spawnBurstParticles(serverLevel, pos, dustColor(type));
                    serverLevel.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.2F);
                }
            }
        }
        if (elapsed >= type.duration()) {
            pedestal.stopRitual();
        }
    }

    /** 仪式产物 */
    private static Item product(DisplayPedestalBlockEntity.RitualType type) {
        return switch (type) {
            case AMULET -> EndboundEmber.DAWN_AMULET.get();
            case ANAHITA -> EndboundEmber.ANAHITA_FIRSTLIGHT_LIDDELL.get();
            case LIFE_STAR -> EndboundEmber.LIFE_STAR.get();
            default -> EndboundEmber.STARDUST_WITCH_HALO.get();
        };
    }

    /** 仪式粒子配色 */
    private static Vector3f dustColor(DisplayPedestalBlockEntity.RitualType type) {
        return switch (type) {
            case AMULET, ANAHITA -> AMULET_DUST_COLOR;
            case LIFE_STAR -> LIFE_STAR_DUST_COLOR;
            default -> HALO_DUST_COLOR;
        };
    }

    /** 仪式完成瞬间：清空环绕展示台上被消耗的虚无星尘，让仪式材料与手绳一同消失 */
    private static void consumeAltarMaterials(Level level, DisplayPedestalBlockEntity center) {
        for (BlockPos source : center.getAltarPedestals()) {
            if (level.getBlockEntity(source) instanceof DisplayPedestalBlockEntity pedestal) {
                pedestal.setStoredItem(ItemStack.EMPTY);
            }
        }
    }

    /** 汇聚粒子：从每个环绕展示台上方沿拱形曲线流向中心 */
    private static void spawnConvergingParticles(ServerLevel level, BlockPos center,
            DisplayPedestalBlockEntity pedestal, long elapsed, Vector3f color) {
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
                level.sendParticles(new DustParticleOptions(color, 0.9F),
                        x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (k == 0) {
                    level.sendParticles(ParticleTypes.END_ROD,
                            x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    /** 完成瞬间的烟花式爆发：闪光、爆炸环、四散的星尘与火花 */
    private static void spawnBurstParticles(ServerLevel level, BlockPos pos, Vector3f color) {
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 1.35D;
        double cz = pos.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FIREWORK, cx, cy, cz, 120, 0.15D, 0.15D, 0.15D, 0.45D);
        level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 80, 0.2D, 0.2D, 0.2D, 0.35D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, cx, cy, cz, 60, 0.3D, 0.3D, 0.3D, 0.5D);
        level.sendParticles(new DustParticleOptions(color, 1.8F),
                cx, cy, cz, 90, 0.4D, 0.4D, 0.4D, 0.25D);
    }

    /**
     * 亡灵风魔法阵：铺在底座表面上、随时间缓慢旋转的两圈魂火符文环，
     * 外加四道斜向刻纹与中心升腾的灵魂、灰烬。
     */
    private static void spawnNecroCircle(ServerLevel level, BlockPos pos, long elapsed) {
        double cx = pos.getX() + 0.5D;
        double cz = pos.getZ() + 0.5D;
        // 贴在底座顶面（展示台摆在底座上，底座顶面即展示台所在层的地面）
        double y = pos.getY() + 0.06D;
        double spin = elapsed * 0.06D;
        drawRing(level, cx, y, cz, NECRO_RING_OUTER, NECRO_RING_OUTER_POINTS, spin, ParticleTypes.SOUL_FIRE_FLAME);
        drawRing(level, cx, y, cz, NECRO_RING_INNER, NECRO_RING_INNER_POINTS, -spin * 1.4D, ParticleTypes.SOUL);
        // 四个斜角的刻纹，与外圈一起反向旋转
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI * 0.25D + i * Math.PI * 0.5D - spin * 0.6D;
            for (int k = 1; k <= 3; k++) {
                double r = 0.8D + k * 0.32D;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        cx + Math.cos(angle) * r, y, cz + Math.sin(angle) * r,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        // 中心的黑烟柱与飘散的灰烬
        level.sendParticles(ParticleTypes.SOUL, cx, pos.getY() + 1.1D, cz, 2, 0.12D, 0.25D, 0.12D, 0.01D);
        level.sendParticles(ParticleTypes.ASH, cx, pos.getY() + 0.35D, cz, 2, 0.85D, 0.05D, 0.85D, 0.01D);
    }

    /** 在地面上画一圈粒子 */
    private static void drawRing(ServerLevel level, double cx, double y, double cz, double radius,
            int points, double rotation, net.minecraft.core.particles.SimpleParticleType particle) {
        double step = Math.PI * 2.0D / points;
        for (int i = 0; i < points; i++) {
            double angle = rotation + i * step;
            level.sendParticles(particle, cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 四角下界之星的能量沿拱形轨迹汇向中心 */
    private static void spawnSoulStreams(ServerLevel level, BlockPos pos, DisplayPedestalBlockEntity pedestal,
            long elapsed) {
        List<BlockPos> sources = pedestal.getAltarPedestals();
        if (sources.isEmpty()) {
            return;
        }
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 1.3D;
        double cz = pos.getZ() + 0.5D;
        for (int i = 0; i < sources.size(); i++) {
            BlockPos source = sources.get(i);
            double sx = source.getX() + 0.5D;
            double sy = source.getY() + 1.45D;
            double sz = source.getZ() + 0.5D;
            // 控制点抬到两端之上，让轨迹呈拱形而不是直线
            double px = (sx + cx) * 0.5D;
            double py = Math.max(sy, cy) + 1.2D;
            double pz = (sz + cz) * 0.5D;
            for (int k = 0; k < PARTICLES_PER_SOURCE; k++) {
                int phase = (int) ((elapsed + i * 5L + k * (PARTICLE_TRAVEL_TICKS / PARTICLES_PER_SOURCE))
                        % PARTICLE_TRAVEL_TICKS);
                double t = phase / (double) PARTICLE_TRAVEL_TICKS;
                double inv = 1.0D - t;
                double x = inv * inv * sx + 2.0D * inv * t * px + t * t * cx;
                double y = inv * inv * sy + 2.0D * inv * t * py + t * t * cy;
                double z = inv * inv * sz + 2.0D * inv * t * pz + t * t * cz;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                if (k == 0) {
                    level.sendParticles(ParticleTypes.SOUL, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }

    /** 汇聚到中心的能量球：越接近召唤时刻越亮、越密，最后 5 秒伴随充能音效 */
    private static void spawnSummonCharge(ServerLevel level, BlockPos pos, long elapsed, int summonTick) {
        double progress = elapsed / (double) summonTick;
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 1.3D;
        double cz = pos.getZ() + 0.5D;
        int count = 1 + (int) (progress * 14.0D);
        double spread = 0.45D * (1.0D - progress * 0.75D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz,
                count, spread, spread * 0.6D, spread, 0.01D);
        level.sendParticles(ParticleTypes.SOUL, cx, cy, cz,
                Math.max(1, count / 2), spread, spread, spread, 0.0D);
        if (summonTick - elapsed <= 100 && elapsed % 20 == 0) {
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS,
                    1.0F, 0.7F + (float) progress * 0.8F);
        }
    }

    /** 召唤瞬间：闪光、爆炸粒子与爆炸音效 */
    private static void spawnSummonBurst(ServerLevel level, BlockPos pos) {
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + 1.2D;
        double cz = pos.getZ() + 0.5D;
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.4F, 0.7F);
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 2, 0.6D, 0.3D, 0.6D, 0.0D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cx, cy, cz, 160, 0.9D, 0.7D, 0.9D, 0.35D);
        level.sendParticles(ParticleTypes.SOUL, cx, cy, cz, 120, 1.2D, 0.9D, 1.2D, 0.25D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 60, 1.0D, 0.6D, 1.0D, 0.05D);
    }

    /** 在法阵中心生成 Herobrine */
    private static void summonHerobrine(ServerLevel level, BlockPos pos) {
        Herobrine herobrine = ModEntities.HEROBRINE.get().create(level);
        if (herobrine == null) {
            return;
        }
        herobrine.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                level.getRandom().nextFloat() * 360.0F, 0.0F);
        // 仪式召唤的 Boss 不因距离过远被自然清除
        herobrine.setPersistenceRequired();
        // 仪式路径不会走 finalizeSpawn，这里手动抽取词条轮换起点
        herobrine.rollStartingTitle();
        level.addFreshEntity(herobrine);
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
        // 祭坛已完整：右键任意一处展示台都按中心启动仪式，环绕位上的材料也不允许取回
        if (RitualAltar.find(level, pos).ok()) {
            tryStartRitual(level, pos, player);
            return ItemInteractionResult.SUCCESS;
        }
        // 台面放着仪式核心（星尘手绳 / 拂晓附魔书 / 晨曦护符 / 末影水晶）：无论手持什么都只尝试启动仪式
        if (RitualAltar.isCore(stored)) {
            // 末影水晶与晨曦护符在祭坛不完整时允许直接取回，避免被卡在台面上拿不下来
            if (!tryStartRitual(level, pos, player) && canTakeBackWhenIncomplete(stored)) {
                takeBack(level, pos, player, pedestal);
            }
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
        // 祭坛已完整：右键任意一处展示台都按中心启动仪式，环绕位上的材料也不允许取回
        if (RitualAltar.find(level, pos).ok()) {
            tryStartRitual(level, pos, player);
            return InteractionResult.SUCCESS;
        }
        // 台面放着仪式核心：尝试启动仪式
        if (RitualAltar.isCore(stored)) {
            if (!tryStartRitual(level, pos, player) && canTakeBackWhenIncomplete(stored)) {
                takeBack(level, pos, player, pedestal);
            }
            return InteractionResult.SUCCESS;
        }
        takeBack(level, pos, player, pedestal);
        return InteractionResult.SUCCESS;
    }

    /** 尝试启动仪式：祭坛结构完整时开始并返回 true，否则在动作栏提示第一处不合格的坐标 */
    private static boolean tryStartRitual(Level level, BlockPos pos, Player player) {
        RitualAltar.Result result = RitualAltar.find(level, pos);
        if (!result.ok()) {
            RitualAltar.report(player, level, result);
            return false;
        }
        RitualAltar.Match match = result.match();
        if (!(level.getBlockEntity(match.center()) instanceof DisplayPedestalBlockEntity centerPedestal)) {
            return false;
        }
        startRitual(level, match.center(), player, centerPedestal, match.pedestals(),
                match.definition().type());
        return true;
    }

    /** 启动仪式；光环仪式需要扣除经验，晨曦护符仪式不消耗经验 */
    private static void startRitual(Level level, BlockPos pos, Player player, DisplayPedestalBlockEntity pedestal,
            List<BlockPos> altar, DisplayPedestalBlockEntity.RitualType type) {
        if (type == DisplayPedestalBlockEntity.RitualType.HALO && !player.getAbilities().instabuild) {
            // 部分命令或编辑器只修改等级而不同步 totalExperience，按等级换算兜底
            int available = ExperienceUtil.availableExperience(player);
            if (available < EndboundEmber.HALO_RITUAL_EXPERIENCE_COST) {
                player.displayClientMessage(
                        Component.translatable("block.endbound_ember.display_pedestal.not_enough_experience"), true);
                return;
            }
            // 先把可能滞后的总经验校正为真实可用值，再精确扣除
            player.totalExperience = available;
            consumeExperience(player, EndboundEmber.HALO_RITUAL_EXPERIENCE_COST);
        }
        pedestal.startRitual(level.getGameTime(), altar, type);
        level.playSound(null, pos, EndboundEmber.HALO_RITUAL.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    type == DisplayPedestalBlockEntity.RitualType.HEROBRINE
                            ? ParticleTypes.SOUL_FIRE_FLAME
                            : ParticleTypes.END_ROD,
                    pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D,
                    60, 1.2D, 0.4D, 1.2D, 0.05D);
        }
        if (type == DisplayPedestalBlockEntity.RitualType.HALO && player instanceof ServerPlayer serverPlayer) {
            var server = serverPlayer.getServer();
            if (server != null) {
                var advancement = server.getAdvancements().get(EndboundEmber.STARDUST_BLESSING_ADVANCEMENT);
                if (advancement != null) {
                    serverPlayer.getAdvancements().award(advancement, EndboundEmber.STARDUST_BLESSING_CRITERION);
                }
            }
        }
    }

    /**
     * 精确扣除经验值。
     *
     * <p>{@code Player#giveExperiencePoints} 传入负数时，进度条会被一次性减去
     * {@code cost / getXpNeededForNextLevel()}，随后 while 循环逐点借位扣等级，
     * 实际会一直扣到经验见底，而不是只扣指定数值。这里改为直接结算总经验，
     * 再按原版公式回推等级与进度。</p>
     */
    private static void consumeExperience(Player player, int cost) {
        int remaining = player.totalExperience - cost;
        player.experienceLevel = 0;
        player.experienceProgress = 0.0F;
        player.totalExperience = 0;
        player.giveExperiencePoints(remaining);
    }

    /** 祭坛不完整时仍允许取回的仪式核心物品。 */
    private static boolean canTakeBackWhenIncomplete(ItemStack stored) {
        return stored.is(Items.NETHER_STAR) || stored.is(Items.END_CRYSTAL)
                || stored.is(EndboundEmber.DAWN_AMULET.get())
                || stored.is(EndboundEmber.ANAHITA_FIRSTLIGHT_LIDDELL.get());
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
