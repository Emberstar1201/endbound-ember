package com.emberstar.endbounember.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** 静止的星尘女巫：仅由服务端事件生成并在短时间后给予奖励。 */
public class EmberStarSorceress extends Mob {
    public static final int LIFETIME_TICKS = 100;
    /** 自然刷新标记：由服务端生成逻辑写入，指令召唤的个体不会带有此标记 */
    private static final String NATURAL_TAG = "EndboundEmberNaturalSorceress";

    /** 是否为自然刷新的个体（指令召唤的返回 false） */
    public boolean isNaturalSpawn() {
        return this.getPersistentData().getBoolean(NATURAL_TAG);
    }

    /** 标记为自然刷新，成就判定只认这种个体 */
    public void markNaturalSpawn() {
        this.getPersistentData().putBoolean(NATURAL_TAG, true);
    }

    public EmberStarSorceress(EntityType<? extends EmberStarSorceress> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.noPhysics = true;
        this.setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        // 无主动移动、索敌或攻击目标。
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
        // 无碰撞。
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void kill() {
        // 防止 /kill 或其他直接 kill 调用提前移除实体；奖励流程由服务端事件控制。
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(0.0D, 0.0D, 0.0D);
        this.setNoGravity(true);
    }
}
