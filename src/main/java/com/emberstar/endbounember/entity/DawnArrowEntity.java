package com.emberstar.endbounember.entity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.enchantment.FreeWillEnchantments;
import com.emberstar.endbounember.item.DawnBreakBowItem;

import net.minecraft.core.Position;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class DawnArrowEntity extends AbstractArrow {
    // AbstractArrow multiplies this coefficient by impact speed before rounding.
    private static final double BASE_DAMAGE = 40.0D / DawnBreakBowItem.FULL_DRAW_SPEED;

    // Boss bars have no common entity API. Inspect declared bars without relying on mapped field names.
    private static final ClassValue<List<Field>> BOSS_BARS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            List<Field> fields = new ArrayList<>();
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (ServerBossEvent.class.isAssignableFrom(field.getType())
                            && !java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.trySetAccessible()) {
                        fields.add(field);
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    private boolean resolvingHit;
    private boolean confirmedDamage;
    // Retain the exact stack for growth while this arrow is loaded. Damage uses the vanilla snapshot.
    @Nullable
    private ItemStack originatingBow;

    public DawnArrowEntity(EntityType<? extends DawnArrowEntity> type, Level level) {
        super(type, level);
        setBaseDamage(BASE_DAMAGE);
    }

    public DawnArrowEntity(Level level, LivingEntity shooter, ItemStack ammo, @Nullable ItemStack weapon) {
        super(ModEntities.DAWN_ARROW.get(), shooter, level, ammo, weapon);
        pickup = Pickup.ALLOWED;
        setBaseDamage(BASE_DAMAGE);
        if (weapon != null && weapon.getItem() instanceof DawnBreakBowItem) originatingBow = weapon;
    }

    public ItemStack heldOriginatingBow(LivingEntity shooter) {
        return originatingBow != null && (shooter.getMainHandItem() == originatingBow || shooter.getOffhandItem() == originatingBow)
                ? originatingBow : ItemStack.EMPTY;
    }

    public DawnArrowEntity(Level level, Position pos, ItemStack ammo) {
        super(ModEntities.DAWN_ARROW.get(), pos.x(), pos.y(), pos.z(), level, ammo, null);
        setBaseDamage(BASE_DAMAGE);
    }

    @Override
    public void tick() {
        super.tick();
        // 临时的黎明箭飞行粒子示例：后续可直接替换为专用粒子，不影响箭的逻辑。
        if (level().isClientSide && !inGround && tickCount % 2 == 0) {
            level().addParticle(EndboundEmber.FLAME_OF_THE_TORCH_0.get(),
                    getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            level().addParticle(EndboundEmber.FLAME_OF_THE_TORCH_1.get(),
                    getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(EndboundEmber.DAWN_ARROW.get());
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        if (level().isClientSide) {
            super.onHitEntity(hit);
            return;
        }
        resolvingHit = true;
        confirmedDamage = false;
        try {
            super.onHitEntity(hit);
            if (!confirmedDamage || random.nextFloat() >= 0.2F) return;
            Entity owner = getOwner();
            var spark = damageSources().source(FreeWillEnchantments.VOID_SPARK, this, owner != null ? owner : this);
            Entity target = hit.getEntity();
            target.hurt(spark, 40.0F);
            // 判罚之雷的额外伤害生效时落下一道纯表现闪电，让玩家知道这次触发了。
            spawnJudgementThunder(target);
        } finally {
            resolvingHit = false;
            confirmedDamage = false;
        }
    }

    /** 在目标位置生成一道不点火、不伤害的落雷，用于表现判罚之雷的额外伤害。 */
    private void spawnJudgementThunder(Entity target) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        DawnJudgementThunder thunder = ModEntities.DAWN_JUDGEMENT_THUNDER.get().create(serverLevel);
        if (thunder == null) return;
        thunder.moveTo(target.getX(), target.getY(), target.getZ());
        serverLevel.addFreshEntity(thunder);
    }

    private static boolean hasBossBar(LivingEntity target) {
        if (target instanceof EnderDragon dragon) {
            var fight = dragon.getDragonFight();
            return fight != null && dragon.getUUID().equals(fight.getDragonUUID()) && hasVisibleBossBar(fight);
        }
        return hasVisibleBossBar(target);
    }

    private static boolean hasVisibleBossBar(Object owner) {
        for (Field field : BOSS_BARS.get(owner.getClass())) {
            try {
                if (field.get(owner) instanceof ServerBossEvent) return true;
            } catch (IllegalAccessException ignored) {
                // The field was made accessible during lookup.
            }
        }
        return false;
    }

    @EventBusSubscriber(modid = EndboundEmber.MODID)
    public static final class Events {
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onArrowDamage(LivingIncomingDamageEvent event) {
            var source = event.getSource();
            if (!source.is(DamageTypes.ARROW) || !(source.getDirectEntity() instanceof DawnArrowEntity arrow)
                    || !arrow.resolvingHit) return;
            LivingEntity target = event.getEntity();
            float multiplier = target.getType().is(EntityTypeTags.UNDEAD) ? 2.5F : 1.0F;
            if (hasBossBar(target)) multiplier *= 3.4F;
            event.setAmount(event.getAmount() * multiplier);
        }

        @SubscribeEvent
        public static void onArrowDamageApplied(LivingDamageEvent.Post event) {
            var source = event.getSource();
            if (source.is(DamageTypes.ARROW) && source.getDirectEntity() instanceof DawnArrowEntity arrow
                    && arrow.resolvingHit && event.getNewDamage() > 0.0F) {
                arrow.confirmedDamage = true;
            }
        }
    }
}
