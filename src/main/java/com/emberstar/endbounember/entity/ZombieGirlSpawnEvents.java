package com.emberstar.endbounember.entity;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 丧尸娘与溺尸娘的独立补刷器。
 *
 * <p>该逻辑只在玩家附近运行，不参与原版刷怪上限；独立上限和每次生成数量
 * 用来避免绕过原版上限后造成实体堆积。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ZombieGirlSpawnEvents {
    private static final int CHECK_INTERVAL = 100;
    private static final int PLAYER_RANGE = 64;
    private static final int SPAWN_ATTEMPTS = 8;
    private static final int MAX_NEARBY = 12;

    private ZombieGirlSpawnEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % CHECK_INTERVAL != 0) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            removeFarWildGirls(level);
            for (ServerPlayer player : level.players()) {
                trySpawnNearPlayer(level, player);
            }
        }
    }

    private static void removeFarWildGirls(ServerLevel level) {
        for (ZombieGirl entity : level.getEntitiesOfClass(ZombieGirl.class,
                level.getWorldBorder().getCollisionShape().bounds(),
                candidate -> !candidate.isTamed())) {
            if (level.getPlayers(player -> player.distanceToSqr(entity) <= 96.0D * 96.0D).isEmpty()) {
                entity.discard();
            }
        }
    }

    private static void trySpawnNearPlayer(ServerLevel level, ServerPlayer player) {
        if (player.isSpectator()) {
            return;
        }

        int zombieGirls = countNearby(level, player, ModEntities.ZOMBIE_GIRL.get());
        int drownedGirls = countNearby(level, player, ModEntities.DROWNED_GIRL.get());
        if (zombieGirls + drownedGirls >= MAX_NEARBY) {
            return;
        }

        // 和平模式稍微提高检查成功率，但仍受每次最多一只限制。
        int chance = level.getDifficulty() == Difficulty.PEACEFUL ? 4 : 10;
        if (level.random.nextInt(chance) != 0) {
            return;
        }

        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            int x = Mth.floor(player.getX()) + level.random.nextIntBetweenInclusive(-PLAYER_RANGE, PLAYER_RANGE);
            int z = Mth.floor(player.getZ()) + level.random.nextIntBetweenInclusive(-PLAYER_RANGE, PLAYER_RANGE);
            BlockPos surface = findSurface(level, x, z);
            if (surface == null || !isNearPlayer(surface, player)) {
                continue;
            }

            EntityType<? extends ZombieGirl> type = level.random.nextBoolean()
                    ? ModEntities.ZOMBIE_GIRL.get()
                    : ModEntities.DROWNED_GIRL.get();
            if (type == ModEntities.DROWNED_GIRL.get() && !isWaterSpawn(level, surface)) {
                continue;
            }
            if (type == ModEntities.ZOMBIE_GIRL.get() && level.getMaxLocalRawBrightness(surface) > 15
                    && level.random.nextInt(4) != 0) {
                continue;
            }
            if (type == ModEntities.ZOMBIE_GIRL.get() && zombieGirls >= MAX_NEARBY / 2) {
                type = ModEntities.DROWNED_GIRL.get();
            } else if (type == ModEntities.DROWNED_GIRL.get() && drownedGirls >= MAX_NEARBY / 2) {
                type = ModEntities.ZOMBIE_GIRL.get();
            }

            ZombieGirl entity = type.create(level);
            if (entity == null || !canSpawnAt(level, entity, surface, type == ModEntities.DROWNED_GIRL.get())) {
                continue;
            }

            entity.moveTo(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F, 0.0F);
            entity.finalizeSpawn(level, level.getCurrentDifficultyAt(surface), MobSpawnType.EVENT, null);
            level.addFreshEntity(entity);
            return;
        }
    }

    private static int countNearby(ServerLevel level, ServerPlayer player, EntityType<?> type) {
        return level.getEntitiesOfClass(ZombieGirl.class,
                        player.getBoundingBox().inflate(PLAYER_RANGE),
                        entity -> entity.getType() == type)
                .size();
    }

    private static boolean isNearPlayer(BlockPos pos, ServerPlayer player) {
        return pos.distSqr(player.blockPosition()) <= (double) PLAYER_RANGE * PLAYER_RANGE;
    }

    private static BlockPos findSurface(ServerLevel level, int x, int z) {
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (top <= level.getMinBuildHeight() || top >= level.getMaxBuildHeight()) {
            return null;
        }
        return new BlockPos(x, top, z);
    }

    private static boolean isWaterSpawn(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER)
                || level.getFluidState(pos.below()).is(FluidTags.WATER);
    }

    private static boolean canSpawnAt(ServerLevel level, Mob entity, BlockPos pos, boolean drowned) {
        if (drowned) {
            if (!level.getFluidState(pos).is(FluidTags.WATER)) {
                return false;
            }
        } else {
            if (!level.getBlockState(pos.below()).blocksMotion()) {
                return false;
            }
            if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) {
                return false;
            }
        }
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        return level.noCollision(entity)
                && level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(0.1D)).isEmpty();
    }
}
