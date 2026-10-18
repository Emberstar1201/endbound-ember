package com.emberstar.endbounember.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;

/**
 * 丧尸娘的索敌 AI：只仇视原版亡灵（{@link ModTags.EntityTypes#ZOMBIE_GIRL_ENEMIES}），
 * 同类与友好生物永远不会成为目标。
 */
public class ZombieGirlTargetingGoal extends TargetGoal {
    private static final TargetingConditions HATE_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight();

    private LivingEntity potentialTarget;
    private int cooldown;

    public ZombieGirlTargetingGoal(PathfinderMob mob) {
        super(mob, false, false);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        this.cooldown = 20;
        this.potentialTarget = findHatedTarget();
        return this.potentialTarget != null;
    }

    @Nullable
    private LivingEntity findHatedTarget() {
        if (this.mob instanceof ZombieGirl zombieGirl && zombieGirl.isTamed()) {
            net.minecraft.world.entity.player.Player owner = zombieGirl.getOwnerPlayer();
            if (owner != null) {
                LivingEntity ownerTarget = owner.getLastHurtMob();
                if (isValidTarget(ownerTarget) && HATE_TARGETING.test(this.mob, ownerTarget)) {
                    return ownerTarget;
                }
                LivingEntity attacker = owner.getLastHurtByMob();
                if (isValidTarget(attacker) && HATE_TARGETING.test(this.mob, attacker)) {
                    return attacker;
                }

                AABB ownerArea = owner.getBoundingBox().inflate(this.getFollowDistance(), 4.0D, this.getFollowDistance());
                LivingEntity nearby = findNearestValid(ownerArea);
                if (nearby != null) {
                    return nearby;
                }
            }
        }

        return findNearestValid(this.mob.getBoundingBox().inflate(this.getFollowDistance(), 4.0D, this.getFollowDistance()));
    }

    @Nullable
    private LivingEntity findNearestValid(AABB area) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : this.mob.level().getEntitiesOfClass(LivingEntity.class, area, this::isValidTarget)) {
            if (!HATE_TARGETING.test(this.mob, candidate)) continue;
            double distance = this.mob.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private boolean isValidTarget(@Nullable LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return false;
        // 同类（丧尸娘、溺尸娘）绝不攻击，避免数据包缺失时误伤
        if (entity instanceof ZombieGirl || entity instanceof DrownedGirl) return false;
        EntityType<?> type = entity.getType();
        if (ZombieGirl.isFriendlySoul(type)) return false;
        // 只仇视原版亡灵，排除同类、玩家与友好生物
        return ZombieGirl.isHated(type) && this.mob.canAttack(entity) && this.mob.canAttackType(type);
    }

    @Override
    public void start() {
        this.mob.setTarget(this.potentialTarget);
        super.start();
    }
}
