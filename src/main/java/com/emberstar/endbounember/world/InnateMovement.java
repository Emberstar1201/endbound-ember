package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 风踏涟漪：陆地与流体表面移动。
 * <p>移速修饰符只在服务端维护；流体表面的贴附只在客户端执行，
 * 因为本地玩家的位置由客户端权威，服务端改坐标会被下一包位置覆盖。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class InnateMovement {
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "wind_ripple_speed");
    private static final double LAND_MULTIPLIER = 1.5D;
    private static final double WATER_MULTIPLIER = 1.25D;
    private static final double LAVA_MULTIPLIER = 1.15D;
    /** 玩家脚底高出表面的最大距离；超过视为腾空，不再吸附。 */
    private static final double SURFACE_TOLERANCE_ABOVE = 0.5D;
    /** 与表面的距离小于该值时直接吸附，否则每 tick 给一个上浮速度。 */
    private static final double SNAP_DISTANCE = 0.75D;
    /** 浸在流体中较深处时每 tick 施加的上浮速度。 */
    private static final double FLOAT_SPEED = 0.08D;
    /** 向上搜索流体表面的最大格数；更深视为深水，交还原版游泳。 */
    private static final int MAX_SURFACE_SCAN = 24;

    private InnateMovement() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        // 客户端位置由本地玩家维护，服务端只负责移速修饰符与疲劳。
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;

        boolean active = Config.WIND_RIPPLE_ENABLED.get() && WindRippleToggle.isEnabled(player)
                && !player.isFallFlying() && !player.isPassenger() && !player.getAbilities().flying;
        manageSpeedModifier(serverPlayer, active ? surfaceMultiplier(player) : 1.0D);
        if (active) player.getFoodData().setExhaustion(0.0F);
    }

    /**
     * 客户端调用：把本地玩家贴回流体表面。
     * <p>距离水面较远时改为缓慢上浮，让掉进水里的玩家能自己浮回水面。</p>
     */
    public static void applySurfaceClient(Player player) {
        Double lavaSurface = fluidSurface(player, FluidTags.LAVA);
        if (lavaSurface != null) {
            alignToSurface(player, lavaSurface);
            player.setRemainingFireTicks(0);
            player.setOnGround(true);
            player.fallDistance = 0.0F;
            spawnFluidParticles(player, ParticleTypes.LAVA);
            return;
        }

        Double waterSurface = fluidSurface(player, FluidTags.WATER);
        if (waterSurface != null) {
            alignToSurface(player, waterSurface);
            player.setOnGround(true);
            player.fallDistance = 0.0F;
            spawnFluidParticles(player, ParticleTypes.FALLING_WATER);
            return;
        }

        // 深水里找不到可站立表面时什么也不做，避免在水下误放陆地粒子。
        if (!player.isInWater() && player.onGround()) spawnLandParticles(player);
    }

    /** 服务端按与客户端相同的判定返回移速倍率，保证数值与画面一致。 */
    private static double surfaceMultiplier(Player player) {
        if (fluidSurface(player, FluidTags.LAVA) != null) return LAVA_MULTIPLIER;
        if (fluidSurface(player, FluidTags.WATER) != null) return WATER_MULTIPLIER;
        // 完全浸在水中且够不到水面时不给加成，交给原版游泳与深海涟漪。
        if (player.isInWater()) return 1.0D;
        return LAND_MULTIPLIER;
    }

    /**
     * 计算玩家可以站立的流体表面高度。
     * <p>两种来源：脚部方块不是流体但脚下方块是流体（站在表面上），
     * 或脚部就在流体里（向头顶方向寻找流体柱顶部）。
     * 表面被实体方块盖住（如冰面）或水柱过深时返回 null。</p>
     */
    private static Double fluidSurface(Player player, TagKey<Fluid> fluid) {
        Level level = player.level();
        BlockPos feet = player.blockPosition();

        if (!level.getFluidState(feet).is(fluid) && level.getFluidState(feet.below()).is(fluid)) {
            // 站在流体表面：脚底高于表面太多说明正在跳起，不拉回。
            return player.getY() > feet.getY() + SURFACE_TOLERANCE_ABOVE ? null : (double) feet.getY();
        }
        if (!level.getFluidState(feet).is(fluid)) return null;

        // 位于流体内部：向上找流体柱顶端，顶端上方必须是空气。
        BlockPos.MutableBlockPos pos = feet.mutable();
        for (int i = 0; i < MAX_SURFACE_SCAN; i++) {
            pos.move(Direction.UP);
            if (!level.getFluidState(pos).is(fluid)) {
                if (!level.getBlockState(pos).isAir()) return null;
                return (double) pos.getY();
            }
        }
        return null;
    }

    /** 固定在流体表面；距离较远时先给上浮速度，避免整段瞬移。 */
    private static void alignToSurface(Player player, double surfaceY) {
        Vec3 motion = player.getDeltaMovement();
        // 玩家正在主动上升（按跳跃键游泳）时不干预。
        if (motion.y > 0.0D) return;

        double gap = player.getY() - surfaceY;
        if (gap < -SNAP_DISTANCE) {
            // 还沉在水下较深处：缓慢上浮。
            player.setDeltaMovement(motion.x, FLOAT_SPEED, motion.z);
            player.hasImpulse = true;
            return;
        }
        if (Math.abs(gap) > 0.001D) player.setPos(player.getX(), surfaceY, player.getZ());
        player.setDeltaMovement(motion.x, 0.0D, motion.z);
    }

    private static void manageSpeedModifier(ServerPlayer player, double multiplier) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) return;
        AttributeModifier existing = instance.getModifier(SPEED_ID);
        if (multiplier <= 1.0D) {
            if (existing != null) instance.removeModifier(SPEED_ID);
            return;
        }
        double amount = multiplier - 1.0D;
        if (existing != null && existing.amount() == amount) return;
        if (existing != null) instance.removeModifier(SPEED_ID);
        instance.addTransientModifier(new AttributeModifier(SPEED_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private static boolean shouldSpawn(Player player, int interval, double minSpeedSqr) {
        if (!player.level().isClientSide() || player.level().getGameTime() % interval != 0L) return false;
        Vec3 motion = player.getDeltaMovement();
        return motion.x * motion.x + motion.z * motion.z >= minSpeedSqr;
    }

    private static void spawnFluidParticles(Player player, ParticleOptions particle) {
        if (!shouldSpawn(player, 3, 0.01D)) return;
        Level level = player.level();
        for (int i = 0; i < 2; i++) {
            double ox = (level.random.nextDouble() - 0.5D) * 0.4D;
            double oz = (level.random.nextDouble() - 0.5D) * 0.4D;
            level.addParticle(particle, player.getX() + ox, player.getY() + 0.05D, player.getZ() + oz, 0.0D, 0.05D, 0.0D);
        }
    }

    private static void spawnLandParticles(Player player) {
        if (!shouldSpawn(player, 2, 0.015D)) return;
        Level level = player.level();
        Vec3 motion = player.getDeltaMovement();
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal < 0.001D) horizontal = 0.001D;
        BlockState ground = level.getBlockState(player.blockPosition().below());
        ParticleOptions particle = ground.isAir() ? ParticleTypes.POOF : new BlockParticleOption(ParticleTypes.BLOCK, ground);
        for (int i = 0; i < 2; i++) {
            double backX = -(motion.x / horizontal) * 0.2D;
            double backZ = -(motion.z / horizontal) * 0.2D;
            double randX = (level.random.nextDouble() - 0.5D) * 0.3D;
            double randZ = (level.random.nextDouble() - 0.5D) * 0.3D;
            level.addParticle(particle, player.getX() + backX + randX, player.getY() + 0.01D, player.getZ() + backZ + randZ,
                    (level.random.nextDouble() - 0.5D) * 0.1D, level.random.nextDouble() * 0.05D + 0.02D,
                    (level.random.nextDouble() - 0.5D) * 0.1D);
        }
    }
}
