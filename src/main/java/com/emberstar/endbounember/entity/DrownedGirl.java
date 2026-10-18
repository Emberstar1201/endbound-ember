package com.emberstar.endbounember.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** 溺尸娘实体：保留水下行为，同时复用丧尸娘的驯服与容器功能。 */
public class DrownedGirl extends ZombieGirl {
    public DrownedGirl(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        // 无敌人时四处游动或漫步，避免站在原地不动
        this.goalSelector.addGoal(5, new RandomSwimmingGoal(this, 1.0D, 20));
        this.goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.RandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(6, new WildLandWanderGoal(this));
        // 优先寻找岸边；上岸后不再被水下游泳目标抢回水中。
        this.goalSelector.addGoal(4, new SurfaceWaterGoal(this));
        // 水下无目标时缓慢下沉，避免漂浮在水面。
        this.goalSelector.addGoal(7, new SinkInWaterGoal(this));
    }

    private static final class WildLandWanderGoal extends Goal {
        private final DrownedGirl mob;
        private int cooldown;
        WildLandWanderGoal(DrownedGirl mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }
        @Override public boolean canUse() {
            return !mob.isTamed() && !mob.isInWater() && mob.getTarget() == null && --cooldown <= 0;
        }
        @Override public boolean canContinueToUse() {
            return !mob.isTamed() && !mob.isInWater() && mob.getTarget() == null
                    && mob.getNavigation().isInProgress();
        }
        @Override public void start() {
            cooldown = 30 + mob.getRandom().nextInt(60);
            for (int i = 0; i < 12; i++) {
                double x = mob.getX() + (mob.getRandom().nextDouble() - 0.5D) * 16.0D;
                double z = mob.getZ() + (mob.getRandom().nextDouble() - 0.5D) * 16.0D;
                BlockPos pos = BlockPos.containing(x, mob.getY(), z);
                if (mob.level().getFluidState(pos).isEmpty()
                        && mob.level().getBlockState(pos.below()).blocksMotion()
                        && mob.getNavigation().moveTo(x, pos.getY(), z, 1.0D)) return;
            }
        }
    }

    /** 生成即携带三叉戟，满耐久，未驯服时也可以从背包中取出。 */
    @Override
    public SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            net.minecraft.world.entity.MobSpawnType reason,
            @Nullable SpawnGroupData spawnData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);
        ItemStack trident = new ItemStack(Items.TRIDENT);
        if (this.getMainHandItem().isEmpty()) this.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, trident);
        else this.setCompanionStack(0, trident);
        return data;
    }

    /** 主手可用的三叉戟或背包中的三叉戟。 */
    @Override
    public ItemStack findCompanionTrident() {
        if (this.getMainHandItem().is(Items.TRIDENT)) return this.getMainHandItem();
        for (int i = 0; i < this.getCompanionInventorySize(); i++) {
            ItemStack stack = this.getCompanionStack(i);
            if (stack.is(Items.TRIDENT)) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSunSensitive() {
        return false;
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected boolean isTamingFood(ItemStack stack) {
        return super.isTamingFood(stack);
    }

    /** 潜水中时持续下沉，并使身体保持在水中。 */
    private static final class SinkInWaterGoal extends Goal {
        private final DrownedGirl mob;
        private int cooldown;

        SinkInWaterGoal(DrownedGirl mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return mob.isInWater() && !mob.isTamed() && !mob.isAggressive() && mob.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (--this.cooldown > 0) return;
            this.cooldown = 10;
            mob.setDeltaMovement(mob.getDeltaMovement().add(0.0D, -0.04D, 0.0D));
        }
    }

    /** 水下没有可攻击目标且已上浮到水面时，主动上岸。 */
    private static final class SurfaceWaterGoal extends Goal {
        private final DrownedGirl mob;
        private int cooldown;

        SurfaceWaterGoal(DrownedGirl mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            if (!mob.isInWater()) return false;
            LivingEntity target = mob.getTarget();
            if (target != null && target.isAlive() && target.isInWater()) return false;
            return mob.isEyeInFluid(FluidTags.WATER);
        }

        @Override
        public boolean canContinueToUse() {
            return mob.isInWater() && mob.isEyeInFluid(FluidTags.WATER);
        }

        @Override
        public void tick() {
            if (--this.cooldown > 0) return;
            this.cooldown = 10;
            mob.setDeltaMovement(mob.getDeltaMovement().add(0.0D, 0.06D, 0.0D));
            BlockPos shore = findShore();
            if (shore != null) {
                mob.getNavigation().moveTo(shore.getX() + 0.5D, shore.getY() + 1.0D, shore.getZ() + 0.5D, 1.2D);
            }
        }

        @Nullable
        private BlockPos findShore() {
            BlockPos origin = mob.blockPosition();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -1, -8), origin.offset(8, 2, 8))) {
                if (!mob.level().getFluidState(pos).isEmpty()) continue;
                if (!mob.level().getFluidState(pos.below()).isEmpty()) continue;
                if (!mob.level().getBlockState(pos.below()).blocksMotion()) continue;
                if (!mob.level().getBlockState(pos).getCollisionShape(mob.level(), pos).isEmpty()) continue;
                double distance = pos.distSqr(origin);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos.immutable();
                }
            }
            return best;
        }
    }
}
