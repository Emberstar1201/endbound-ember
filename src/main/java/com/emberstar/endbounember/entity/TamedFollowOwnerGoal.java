package com.emberstar.endbounember.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;

/** 已驯服丧尸娘/溺尸娘的跟随目标，优先于水域移动。 */
public final class TamedFollowOwnerGoal extends Goal {
    private final ZombieGirl mob;
    private final double speed;
    private Player owner;

    public TamedFollowOwnerGoal(ZombieGirl mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mob.isTamed() || mob.getTarget() != null) return false;
        Player player = mob.getOwnerPlayer();
        if (player == null || mob.distanceToSqr(player) < 9.0D) return false;
        this.owner = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null && mob.isTamed() && mob.getTarget() == null && mob.distanceToSqr(owner) > 4.0D;
    }

    @Override
    public void tick() {
        if (owner == null) return;
        mob.getLookControl().setLookAt(owner, 10.0F, mob.getMaxHeadXRot());
        if (mob.distanceToSqr(owner) > 144.0D && tryTeleport()) return;
        mob.getNavigation().moveTo(owner, speed);
    }

    private boolean tryTeleport() {
        if (!owner.onGround() || !mob.onGround()) return false;
        BlockPos origin = owner.blockPosition();
        for (int radius = 2; radius <= 4; radius++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) != radius && Math.abs(z) != radius) continue;
                    for (int y = 0; y <= 2; y++) {
                        BlockPos feet = origin.offset(x, y, z);
                        if (!isSafe(feet)) continue;
                        mob.teleportTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
                        mob.getNavigation().stop();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isSafe(BlockPos feet) {
        BlockPos below = feet.below();
        return mob.level().getBlockState(below).isSolid()
                && mob.level().getFluidState(feet).isEmpty()
                && mob.level().getFluidState(feet.above()).isEmpty()
                && mob.level().noCollision(mob, new AABB(feet).move(0.5D, 0.0D, 0.5D));
    }

    @Override
    public void stop() {
        owner = null;
        mob.getNavigation().stop();
    }
}
