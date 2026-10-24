package com.emberstar.endbounember.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * 「刺破长夜的黎明」剑气视觉实体。
 *
 * 沿视线直线飞行 15 格、无重力、可穿透方块，伤害由 {@code DawnEnchantment} 在释放瞬间结算，
 * 此实体只负责月牙形粒子表现（颜色 #FCF6F8）；人权剑挥出的剑气改用灵魂火粒子。
 */
public class DawnSwordQiEntity extends Projectile {
    /** 剑气颜色 #FCF6F8。 */
    private static final Vector3f QI_COLOR = new Vector3f(0.988F, 0.965F, 0.973F);
    private static final double SPEED = 1.2D;
    private static final EntityDataAccessor<Boolean> DATA_SOUL_FIRE =
            SynchedEntityData.defineId(DawnSwordQiEntity.class, EntityDataSerializers.BOOLEAN);

    private double maxDistance = 15.0D;
    private Vec3 startPos;

    public DawnSwordQiEntity(EntityType<? extends DawnSwordQiEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public DawnSwordQiEntity(Level level, LivingEntity owner, Vec3 pos, Vec3 direction, double maxDistance) {
        this(ModEntities.DAWN_SWORD_QI.get(), level);
        this.setOwner(owner);
        this.setPos(pos.x, pos.y, pos.z);
        this.setDeltaMovement(direction.normalize().scale(SPEED));
        this.maxDistance = maxDistance;
        this.startPos = pos;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SOUL_FIRE, false);
    }

    /** 是否为人权剑挥出的灵魂火剑气（仅影响粒子）。 */
    public boolean isSoulFire() {
        return this.entityData.get(DATA_SOUL_FIRE);
    }

    public void setSoulFire(boolean soulFire) {
        this.entityData.set(DATA_SOUL_FIRE, soulFire);
    }

    @Override
    public void tick() {
        this.baseTick();

        if (this.startPos == null) this.startPos = this.position();

        if (!this.level().isClientSide) {
            Entity owner = this.getOwner();
            if (owner != null && owner.isRemoved()) {
                this.discard();
                return;
            }
            // 只按飞行距离判定消失，穿透方块
            if (this.startPos.distanceToSqr(this.position()) >= this.maxDistance * this.maxDistance) {
                this.discard();
                return;
            }
        }

        this.move(MoverType.SELF, this.getDeltaMovement());

        if (this.level() instanceof ServerLevel serverLevel) {
            this.spawnCrescentParticles(serverLevel);
        }
    }

    /** 在剑气当前位置绘制双层月牙粒子轮廓。 */
    private void spawnCrescentParticles(ServerLevel level) {
        Vec3 pos = this.position();
        Vec3 dir = this.getDeltaMovement();
        if (dir.lengthSqr() < 0.001D) return;
        dir = dir.normalize();

        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        if (Math.abs(dir.y) > 0.99D) up = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 right = dir.cross(up).normalize();
        Vec3 localUp = right.cross(dir).normalize();

        double outerRadius = 0.90D;
        double innerRadius = outerRadius * 0.62D;
        int arcPoints = 15;
        boolean soulFire = this.isSoulFire();

        for (int i = 0; i < arcPoints; i++) {
            double progress = (double) i / (arcPoints - 1);
            double angle = Math.toRadians(-115.0D + progress * 230.0D);

            spawnArcParticle(level, soulFire, pos, right, localUp,
                    Math.cos(angle) * outerRadius,
                    Math.sin(angle) * outerRadius, 0.85F);
            spawnArcParticle(level, soulFire, pos, right, localUp,
                    Math.cos(angle) * innerRadius + outerRadius * 0.28D,
                    Math.sin(angle) * innerRadius, 0.65F);
        }
    }

    private static void spawnArcParticle(ServerLevel level, boolean soulFire, Vec3 center, Vec3 right, Vec3 up,
            double rightOffset, double upOffset, float scale) {
        Vec3 particlePosition = center
                .add(right.scale(rightOffset))
                .add(up.scale(upOffset));
        if (soulFire) {
            // 人权剑剑气：灵魂火粒子，双粒叠加保证月牙轮廓清晰
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    particlePosition.x, particlePosition.y, particlePosition.z,
                    2, 0.03D, 0.03D, 0.03D, 0.01D);
            return;
        }
        level.sendParticles(new DustParticleOptions(QI_COLOR, scale),
                particlePosition.x, particlePosition.y, particlePosition.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("QiMaxDistance", this.maxDistance);
        tag.putBoolean("QiSoulFire", this.isSoulFire());
        if (this.startPos != null) {
            tag.putDouble("QiStartX", this.startPos.x);
            tag.putDouble("QiStartY", this.startPos.y);
            tag.putDouble("QiStartZ", this.startPos.z);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.maxDistance = tag.getDouble("QiMaxDistance");
        this.setSoulFire(tag.getBoolean("QiSoulFire"));
        if (tag.contains("QiStartX")) {
            this.startPos = new Vec3(tag.getDouble("QiStartX"), tag.getDouble("QiStartY"), tag.getDouble("QiStartZ"));
        }
    }
}
