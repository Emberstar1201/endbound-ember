package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.InnateMovementClient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class InnateMovement {
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "wind_ripple_speed");
    private static final double LAND_MULTIPLIER = 1.5D;
    private static final double WATER_MULTIPLIER = 1.25D;
    private static final double LAVA_MULTIPLIER = 1.15D;
    private static final double SURFACE_TOLERANCE_ABOVE = 0.5D;
    private static final double SURFACE_TOLERANCE_BELOW = 1.5D;

    private InnateMovement() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        double multiplier = 1.0D;
        if (Config.WIND_RIPPLE_ENABLED.get() && !player.isFallFlying() && !player.isPassenger() && !player.getAbilities().flying) {
            multiplier = moveOnSurface(player);
        }
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;
        manageSpeedModifier(serverPlayer, multiplier);
        if (Config.WIND_RIPPLE_ENABLED.get()) {
            player.getFoodData().setExhaustion(0.0F);
        }
    }

    private static double moveOnSurface(Player player) {
        Double lavaSurface = surfaceY(player, Fluids.LAVA);
        if (lavaSurface != null) {
            alignToSurface(player, lavaSurface);
            player.setRemainingFireTicks(0);
            player.setOnGround(true);
            player.fallDistance = 0.0F;
            spawnFluidParticles(player, ParticleTypes.LAVA);
            return LAVA_MULTIPLIER;
        }
        Double waterSurface = surfaceY(player, Fluids.WATER);
        if (waterSurface != null) {
            alignToSurface(player, waterSurface);
            player.setOnGround(true);
            player.fallDistance = 0.0F;
            spawnFluidParticles(player, ParticleTypes.FALLING_WATER);
            return WATER_MULTIPLIER;
        }
        if (player.isInWater()) return 1.0D;
        if (player.onGround()) spawnLandParticles(player);
        return LAND_MULTIPLIER;
    }

    private static Double surfaceY(Player player, Fluid fluid) {
        Level level = player.level();
        BlockPos feet = player.blockPosition();
        BlockState feetState = level.getBlockState(feet);
        double surfaceY;
        if (feetState.getFluidState().is(fluid)) {
            if (!level.getBlockState(feet.above()).isAir()) return null;
            surfaceY = feet.getY() + 1.0D;
        } else if (level.getBlockState(feet.below()).getFluidState().is(fluid)) {
            surfaceY = feet.getY();
        } else {
            return null;
        }
        double feetY = player.getY();
        if (feetY < surfaceY - SURFACE_TOLERANCE_BELOW || feetY > surfaceY + SURFACE_TOLERANCE_ABOVE) return null;
        return surfaceY;
    }

    private static void alignToSurface(Player player, double surfaceY) {
        Vec3 motion = player.getDeltaMovement();
        if (motion.y > 0.0D) return;
        if (Math.abs(player.getY() - surfaceY) > 0.001D) player.setPos(player.getX(), surfaceY, player.getZ());
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
