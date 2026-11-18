package com.emberstar.endbounember.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

/** 钢筋铁骨触发的引力黑洞：只影响生物，不破坏方块。 */
public class StardustBlackHoleEntity extends Entity {
    public static final int LIFETIME_TICKS = 20 * 20;
    public static final double EFFECT_RADIUS = 10.0D;
    private static final double PULL_PER_TICK = 5.0D / 20.0D;
    private static final float PULSE_DAMAGE = 20.0F;
    private static final float COLLAPSE_DAMAGE = 100.0F;
    /** 客户端渲染需要知道当前年龄，服务端每 tick 同步一次。 */
    private static final EntityDataAccessor<Integer> DATA_AGE =
            SynchedEntityData.defineId(StardustBlackHoleEntity.class, EntityDataSerializers.INT);
    private static final ResourceKey<net.minecraft.world.damagesource.DamageType> PULSE_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "stardust_black_hole"));
    private static final ResourceKey<net.minecraft.world.damagesource.DamageType> COLLAPSE_DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "stardust_black_hole_collapse"));

    @Nullable
    private UUID ownerUuid;
    private int age;

    public StardustBlackHoleEntity(EntityType<? extends StardustBlackHoleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public StardustBlackHoleEntity(Level level, ServerPlayer owner) {
        this(ModEntities.STARDUST_BLACK_HOLE.get(), level);
        this.ownerUuid = owner.getUUID();
        this.setPos(owner.getX(), owner.getY(), owner.getZ());
    }

    public static void spawnAt(ServerPlayer player) {
        StardustBlackHoleEntity blackHole = new StardustBlackHoleEntity(player.serverLevel(), player);
        player.serverLevel().addFreshEntity(blackHole);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_AGE, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) return;

        tickBlackHole();
    }

    private void tickBlackHole() {
        age++;
        this.entityData.set(DATA_AGE, age);

        if (age >= LIFETIME_TICKS) {
            collapse();
            return;
        }

        pullTargets();
        tickDamagePulse();
    }

    private void tickDamagePulse() {
        if (age % 20 == 0) {
            damageTargets(PULSE_DAMAGE, PULSE_DAMAGE_TYPE);
        }
    }

    private void pullTargets() {
        for (LivingEntity target : targets()) {
            Vec3 offset = this.position().add(0.0D, 0.5D, 0.0D).subtract(target.position());
            double distance = offset.length();
            if (distance <= 0.75D) continue;
            Vec3 pull = offset.scale(Math.min(PULL_PER_TICK, distance - 0.75D) / distance);
            target.setDeltaMovement(target.getDeltaMovement().add(pull));
            target.hurtMarked = true;
        }
    }

    private void damageTargets(float amount, ResourceKey<net.minecraft.world.damagesource.DamageType> type) {
        Entity owner = ownerEntity();
        DamageSource source = owner == null
                ? this.level().damageSources().source(type, this)
                : this.level().damageSources().source(type, owner, this);
        for (LivingEntity target : targets()) {
            target.hurt(source, amount);
        }
    }

    private void collapse() {
        damageTargets(COLLAPSE_DAMAGE, COLLAPSE_DAMAGE_TYPE);
        if (this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    this.getX(), this.getY() + 0.5D, this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
            serverLevel.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.7F);
        }
        this.discard();
    }

    private java.util.List<LivingEntity> targets() {
        AABB area = this.getBoundingBox().inflate(EFFECT_RADIUS);
        return this.level().getEntitiesOfClass(LivingEntity.class, area, this::canAffect);
    }

    private boolean canAffect(LivingEntity target) {
        if (!target.isAlive() || target.isInvulnerable()) return false;
        if (target instanceof ZombieGirl zombieGirl && !zombieGirl.isTamed()) return false;
        if (ownerUuid != null && ownerUuid.equals(target.getUUID())) return false;
        if (target instanceof Player player && player.isSpectator()) return false;
        UUID targetOwner = ownerUuidOf(target);
        return ownerUuid == null || !ownerUuid.equals(targetOwner);
    }

    @Nullable
    private Entity ownerEntity() {
        if (ownerUuid == null) return null;
        return this.level().getPlayerByUUID(ownerUuid);
    }

    @Nullable
    private static UUID ownerUuidOf(LivingEntity target) {
        if (target instanceof TamableAnimal animal && animal.isTame()) {
            return animal.getOwnerUUID();
        }
        if (target instanceof OwnableEntity ownable) {
            return ownable.getOwnerUUID();
        }
        if (target instanceof ZombieGirl zombieGirl) {
            return zombieGirl.getOwnerUUID();
        }
        return null;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(EFFECT_RADIUS);
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
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        tag.putInt("Age", age);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) ownerUuid = tag.getUUID("Owner");
        age = tag.getInt("Age");
        this.entityData.set(DATA_AGE, age);
    }

    /** 已存在的刻数，客户端读取同步值。 */
    public int getAgeTicks() {
        return this.entityData.get(DATA_AGE);
    }

    public float lifeProgress(float partialTick) {
        return Math.min(1.0F, (this.getAgeTicks() + partialTick) / (float) LIFETIME_TICKS);
    }
}
