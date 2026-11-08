package com.emberstar.endbounember.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 下界流星的视觉实体。
 *
 * <p>预警开始时在落点生成，实体本身不移动也不参与战斗：模型由渲染器按
 * {@link #fallProgress(float)} 从高空推下来，拖尾火焰同样由客户端生成。
 * 伤害与击退仍由 {@code Herobrine#resolveTelegraph} 结算，因此即使 BOSS 提前消失，
 * 这颗流星也只会安静地消失，不会留下孤立伤害。</p>
 */
public class NetherMeteor extends Entity {
    /** 从出现到砸地的总时长（tick） */
    public static final int FALL_TICKS = 45;
    /** 起始坠落高度（格） */
    public static final double FALL_HEIGHT = 14.0D;

    public NetherMeteor(EntityType<? extends NetherMeteor> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public NetherMeteor(Level level, Vec3 impactPos) {
        this(ModEntities.NETHER_METEOR.get(), level);
        this.setPos(impactPos.x, impactPos.y, impactPos.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.spawnTrail();
        } else if (this.tickCount >= FALL_TICKS) {
            this.discard();
        }
    }

    /** 坠落进度：0 = 刚出现，1 = 砸到地面 */
    public float fallProgress(float partialTick) {
        return Mth.clamp((this.tickCount + partialTick) / FALL_TICKS, 0.0F, 1.0F);
    }

    /** 模型当前所在的空中位置 */
    public Vec3 visualPosition(float partialTick) {
        return new Vec3(this.getX(),
                this.getY() + FALL_HEIGHT * (1.0D - this.fallProgress(partialTick)),
                this.getZ());
    }

    /** 拖尾火焰：只在客户端生成，避免每 tick 都往外发粒子包 */
    private void spawnTrail() {
        Vec3 pos = this.visualPosition(0.0F);
        for (int i = 0; i < 3; i++) {
            this.level().addParticle(ParticleTypes.FLAME,
                    pos.x + (this.random.nextDouble() - 0.5D) * 0.7D,
                    pos.y + (this.random.nextDouble() - 0.5D) * 0.7D,
                    pos.z + (this.random.nextDouble() - 0.5D) * 0.7D,
                    0.0D, 0.02D, 0.0D);
        }
        if (this.random.nextInt(2) == 0) {
            this.level().addParticle(ParticleTypes.LARGE_SMOKE,
                    pos.x, pos.y - 0.2D, pos.z, 0.0D, 0.01D, 0.0D);
        }
        if (this.random.nextInt(4) == 0) {
            this.level().addParticle(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 整条坠落轨迹都参与视锥剔除，抬头看天空时模型不会被判成“在屏幕外” */
    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().expandTowards(0.0D, FALL_HEIGHT, 0.0D).inflate(1.0D);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 纯视觉实体，无需持久化
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 纯视觉实体，无需持久化
    }
}
