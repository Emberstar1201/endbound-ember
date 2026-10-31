package com.emberstar.endbounember.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 母凋零之首：强化版凋零偶尔发射的巨大头颅。
 *
 * <ul>
 *   <li>飞行 {@link #FLIGHT_TICKS} tick 后自动分裂为 4~5 个普通凋零之首，朝四周散射。</li>
 *   <li>撞到方块或生物时提前分裂，分裂后自身消失。</li>
 *   <li>子头颅继承母头颅的发射者，伤害由原版 {@code WitherSkull} 结算，倍率在 {@code WitherEmpowerment} 中下调。</li>
 * </ul>
 */
public class MotherWitherSkull extends Projectile {
    /** 母头颅飞行 2 秒后自动分裂。 */
    private static final int FLIGHT_TICKS = 40;
    private static final double SPEED = 0.65D;
    private static final int MIN_CHILDREN = 4;
    private static final int MAX_CHILDREN = 5;
    /** 子头颅相对母头颅前进方向的散射角。 */
    private static final double SPREAD = Math.toRadians(28.0D);

    private int life;

    public MotherWitherSkull(EntityType<? extends MotherWitherSkull> type, Level level) {
        super(type, level);
    }

    public MotherWitherSkull(Level level, LivingEntity owner, Vec3 direction) {
        this(ModEntities.MOTHER_WITHER_SKULL.get(), level);
        this.setOwner(owner);
        this.moveTo(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5D, owner.getZ(),
                owner.getYRot(), owner.getXRot());
        this.setDeltaMovement(direction.normalize().scale(SPEED));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        this.baseTick();

        if (!this.level().isClientSide) {
            this.life++;
            if (this.life >= FLIGHT_TICKS) {
                this.split();
                return;
            }
        }

        Vec3 movement = this.getDeltaMovement();
        this.move(MoverType.SELF, movement);

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    this.getX(), this.getY(), this.getZ(), 4, 0.35D, 0.35D, 0.35D, 0.01D);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    this.getX(), this.getY(), this.getZ(), 2, 0.25D, 0.25D, 0.25D, 0.01D);
        }

        if (this.level().isClientSide) return;

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult) {
            this.split();
        } else if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult) {
            this.split();
        } else if (movement.lengthSqr() < 0.001D) {
            // 撞墙后速度归零则立即分裂，避免卡在方块里
            this.split();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && target != this.getOwner();
    }

    /** 母头颅不直接结算伤害，命中只触发分裂。 */
    @Override
    protected void onHit(HitResult result) {
    }

    /** 分裂为 4~5 个普通凋零之首，沿母头颅前进方向呈锥形散射。 */
    private void split() {
        if (!(this.level() instanceof ServerLevel level)) {
            this.discard();
            return;
        }

        Vec3 forward = this.getDeltaMovement();
        forward = forward.lengthSqr() < 0.001D ? this.getLookAngle() : forward.normalize();

        // 以任意不平行于 forward 的向量构造散射基底
        Vec3 reference = Math.abs(forward.y) > 0.9D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(reference).normalize();
        Vec3 up = right.cross(forward).normalize();

        int children = MIN_CHILDREN + level.random.nextInt(MAX_CHILDREN - MIN_CHILDREN + 1);
        Entity owner = this.getOwner();
        // 原版 WitherSkull 构造器要求发射者为非空的 LivingEntity（内部会读取其坐标），
        // 母头颅的发射者可能已死亡或未同步，取不到时退回附近最近的生物，避免空指针崩溃。
        LivingEntity shooter = owner instanceof LivingEntity living ? living
                : level.getNearestPlayer(this, 32.0D);
        if (shooter == null) {
            // 找不到任何可用的发射者时直接消失，避免每 tick 反复尝试分裂
            this.discard();
            return;
        }
        for (int i = 0; i < children; i++) {
            double angle = Math.PI * 2.0D * i / children;
            Vec3 offset = right.scale(Math.cos(angle) * Math.tan(SPREAD))
                    .add(up.scale(Math.sin(angle) * Math.tan(SPREAD)));
            Vec3 direction = forward.add(offset).normalize();

            WitherSkull skull = new WitherSkull(level, shooter, direction);
            skull.setPos(this.getX(), this.getY(), this.getZ());
            skull.setDeltaMovement(direction.scale(0.9D));
            // 打标：子头颅伤害略低于普通头颅，由 WitherEmpowerment 在受击时按比例下调
            skull.getPersistentData().putBoolean(com.emberstar.endbounember.world.WitherEmpowerment.TAG_CHILD_SKULL, true);
            level.addFreshEntity(skull);
        }

        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2.0F, 0.6F);
        level.sendParticles(ParticleTypes.EXPLOSION,
                this.getX(), this.getY(), this.getZ(), 2, 0.4D, 0.4D, 0.4D, 0.0D);
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0D;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("MotherLife", this.life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.life = tag.getInt("MotherLife");
    }
}
