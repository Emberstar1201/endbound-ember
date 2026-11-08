package com.emberstar.endbounember.entity;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;

/**
 * 丧尸娘的索敌 AI：
 * <ul>
 *   <li>主动索敌只仇视原版亡灵（{@link ModTags.EntityTypes#ZOMBIE_GIRL_ENEMIES}）；</li>
 *   <li>驯服后协助主人战斗：主人正在攻击、或攻击主人的任意生物都会成为协助目标（含牛羊猪等动物）；</li>
 *   <li>白名单（同类丧尸娘/溺尸娘、车万女仆）永远不会成为目标。</li>
 * </ul>
 */
public class ZombieGirlTargetingGoal extends TargetGoal {
    private static final TargetingConditions HATE_TARGETING = TargetingConditions.forCombat().ignoreLineOfSight();
    /*
     * 协助主人战斗时使用：不能走 forCombat，因为它的 test 内部还会再校验一次
     * canAttackType，而友好生物标签（牛、羊、猪等）会在那里被否决，
     * 结果就是主人打了动物、少女却只跟随不动手。
     */
    private static final TargetingConditions ASSIST_TARGETING = TargetingConditions.forNonCombat().ignoreLineOfSight();

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
            Player owner = zombieGirl.getOwnerPlayer();
            if (owner != null) {
                // 优先级 1：主人当前攻击的目标（辅助主人战斗，覆盖任意非友好生物）
                LivingEntity ownerTarget = owner.getLastHurtMob();
                if (isValidTarget(ownerTarget, false) && ASSIST_TARGETING.test(this.mob, ownerTarget)) {
                    return ownerTarget;
                }
                // 优先级 2：攻击主人的生物
                LivingEntity attacker = owner.getLastHurtByMob();
                if (isValidTarget(attacker, false) && ASSIST_TARGETING.test(this.mob, attacker)) {
                    return attacker;
                }

                // 优先级 3：主人附近的其他天生仇视目标（仅亡灵）
                AABB ownerArea = owner.getBoundingBox().inflate(this.getFollowDistance(), 4.0D, this.getFollowDistance());
                LivingEntity nearby = findNearestValid(ownerArea, true);
                if (nearby != null) {
                    return nearby;
                }
            }
        }

        // 优先级 4：自身附近的天生仇视目标（仅亡灵）
        return findNearestValid(this.mob.getBoundingBox().inflate(this.getFollowDistance(), 4.0D, this.getFollowDistance()), true);
    }

    @Nullable
    private LivingEntity findNearestValid(AABB area, boolean requireInnateHate) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : this.mob.level().getEntitiesOfClass(LivingEntity.class, area,
                candidate -> isValidTarget(candidate, requireInnateHate))) {
            if (!HATE_TARGETING.test(this.mob, candidate)) continue;
            double distance = this.mob.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * 判断是否可以作为攻击目标。
     *
     * @param requireInnateHate 主动索敌为 true（只攻击天生仇视的原版亡灵）；
     *                          协助主人战斗为 false（主人攻击的任意生物均可协助攻击）。
     */
    private boolean isValidTarget(@Nullable LivingEntity entity, boolean requireInnateHate) {
        if (entity == null || !entity.isAlive()) return false;
        // 白名单：同类（丧尸娘、溺尸娘）与车万女仆（野生/驯服）永远不攻击
        if (isWhitelisted(entity)) return false;
        EntityType<?> type = entity.getType();
        /*
         * 协助主人战斗：除玩家与白名单外一律可打（含牛羊猪等友好生物）。
         * 这里刻意不调用 canAttackType，否则会被友好生物标签拦下，导致少女只跟随不出手。
         */
        if (!requireInnateHate) {
            return !(entity instanceof Player) && this.mob.canAttack(entity);
        }
        // 主动索敌只打天生仇视的原版亡灵
        return ZombieGirl.isHated(type) && this.mob.canAttack(entity) && this.mob.canAttackType(type);
    }

    /** 白名单：同类与车万女仆即便被主人攻击也不协助攻击（敌对女仆妖精不在此列）。 */
    private static boolean isWhitelisted(LivingEntity entity) {
        if (entity instanceof ZombieGirl || entity instanceof DrownedGirl) return true;
        return ZombieGirl.isProtectedMaid(entity.getType());
    }


    @Override
    public void start() {
        this.mob.setTarget(this.potentialTarget);
        super.start();
    }
}
