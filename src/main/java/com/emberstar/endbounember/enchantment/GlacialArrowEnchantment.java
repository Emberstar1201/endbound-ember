package com.emberstar.endbounember.enchantment;

import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.compat.FriendlyMobs;
import com.emberstar.endbounember.network.CloudArrowVelocityPayload;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 琉璃冰魄箭：弓 / 弩专属，最高 I 级。
 * 蓄力速度减半；母箭以约 3.2 倍速度飞行并散射 12 支子箭；
 * 命中后结算范围伤害，并把周围敌人拉向命中点。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class GlacialArrowEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "glacial_arrow"));

    /** 母箭速度倍率。 */
    private static final double SPEED_MULTIPLIER = 3.2D;
    /** 子箭数量。 */
    private static final int SUB_ARROW_COUNT = 12;
    /** 子箭扇形展开的总角度（度）。 */
    private static final double FAN_ANGLE_DEGREES = 30.0D;
    /** 子箭初速相对母箭的倍率。 */
    private static final double SUB_SPEED_MULTIPLIER = 0.8D;
    /** 子箭基础伤害相对母箭的倍率。 */
    private static final double SUB_DAMAGE_MULTIPLIER = 0.6D;
    /** 母箭命中范围伤害半径（格）。 */
    private static final double AOE_RADIUS = 2.5D;
    /** 子箭命中范围伤害半径（格）。 */
    private static final double SUB_AOE_RADIUS = 1.5D;
    /** 母箭范围伤害相对基础伤害的倍率。 */
    private static final float AOE_DAMAGE_MULTIPLIER = 2.5F;
    /** 子箭范围伤害相对基础伤害的倍率。 */
    private static final float SUB_AOE_DAMAGE_MULTIPLIER = 1.5F;
    /** 牵引强度。 */
    private static final double PULL_STRENGTH = 0.6D;
    /** 每 N tick 补回 1 tick 使用时间，使蓄力速度减半。 */
    private static final int SLOW_CHARGE_INTERVAL = 2;
    /** 原版生成包对速度分量的钳制上限（格/刻）。 */
    private static final double VELOCITY_PACKET_LIMIT = 3.9D;

    private static final String TAG_LEVEL = "EndboundEmberGlacialArrowLevel";
    private static final String TAG_SUB = "EndboundEmberGlacialArrowSub";

    private GlacialArrowEnchantment() {
    }

    /** 读取物品上的琉璃冰魄箭附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int level = levelOf(stack, DataComponents.ENCHANTMENTS);
        if (level > 0) return level;
        return levelOf(stack, DataComponents.STORED_ENCHANTMENTS);
    }

    private static int levelOf(ItemStack stack, DataComponentType<ItemEnchantments> type) {
        ItemEnchantments enchantments = stack.getOrDefault(type, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Optional<ResourceKey<Enchantment>> key = entry.getKey().unwrapKey();
            if (key.isPresent() && key.get().equals(KEY)) return entry.getIntValue();
        }
        return 0;
    }

    /** 持有附魔弓 / 弩的等级（主手优先，其次副手）。 */
    private static int levelOfHands(LivingEntity entity) {
        int level = levelOf(entity.getMainHandItem());
        return level > 0 ? level : levelOf(entity.getOffhandItem());
    }

    /** 蓄力速度减半：每隔一 tick 把剩余使用时间补回 1 tick。 */
    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem();
        if (levelOf(stack) <= 0) return;
        if (player.level().getGameTime() % SLOW_CHARGE_INTERVAL != 0L) return;
        event.setDuration(event.getDuration() + 1);
    }

    /** 新射出的箭：标记等级、放大母箭速度并散射子箭。 */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (arrow.tickCount > 0) return;

        CompoundTag data = arrow.getPersistentData();
        if (data.contains(TAG_LEVEL)) return;

        Entity owner = arrow.getOwner();
        if (!(owner instanceof LivingEntity shooter)) return;

        int level = levelOfHands(shooter);
        if (level <= 0) return;

        data.putInt(TAG_LEVEL, level);

        // 子箭在加入世界前已打好标记，不再二次散射
        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(SPEED_MULTIPLIER));
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        spawnSubArrows(arrow, shooter);
    }

    private static void spawnSubArrows(AbstractArrow mother, LivingEntity shooter) {
        Level level = mother.level();
        Vec3 velocity = mother.getDeltaMovement();
        if (velocity.lengthSqr() < 1.0E-6D) return;

        Vec3 direction = velocity.normalize();
        Vec3 right = new Vec3(0.0D, 1.0D, 0.0D).cross(direction);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        }
        right = right.normalize();

        double fan = Math.toRadians(FAN_ANGLE_DEGREES);
        double step = fan / (SUB_ARROW_COUNT - 1);
        double start = -fan / 2.0D;
        double speed = velocity.length() * SUB_SPEED_MULTIPLIER;
        Vec3 center = mother.position().add(direction.scale(1.5D));

        for (int i = 0; i < SUB_ARROW_COUNT; i++) {
            double offset = start + step * i;
            Vec3 subDirection = direction.scale(Math.cos(offset))
                    .add(right.scale(Math.sin(offset)))
                    .normalize();

            AbstractArrow sub = ProjectileUtil.getMobArrow(shooter, new ItemStack(Items.ARROW), 1.0F,
                    ItemStack.EMPTY);
            Vec3 spawnPos = center.add(subDirection.scale(0.5D));
            sub.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            sub.setDeltaMovement(subDirection.scale(speed));
            sub.setBaseDamage(mother.getBaseDamage() * SUB_DAMAGE_MULTIPLIER);
            sub.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            sub.getPersistentData().putInt(TAG_LEVEL, 1);
            sub.getPersistentData().putBoolean(TAG_SUB, true);
            level.addFreshEntity(sub);
        }
    }

    /** 补发真实速度，避免原版生成包把高速分量钳制到 3.9 导致轨迹错位。 */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof AbstractArrow arrow)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Vec3 velocity = arrow.getDeltaMovement();
        boolean clamped = Math.abs(velocity.x) > VELOCITY_PACKET_LIMIT
                || Math.abs(velocity.y) > VELOCITY_PACKET_LIMIT
                || Math.abs(velocity.z) > VELOCITY_PACKET_LIMIT;
        if (!clamped) return;

        PacketDistributor.sendToPlayer(player, new CloudArrowVelocityPayload(
                arrow.getId(), velocity.x, velocity.y, velocity.z));
    }

    /** 命中结算：范围伤害 + 向命中点牵引。 */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;
        Level level = arrow.level();
        if (level.isClientSide()) return;

        CompoundTag data = arrow.getPersistentData();
        if (data.getInt(TAG_LEVEL) <= 0) return;

        boolean sub = data.getBoolean(TAG_SUB);
        double radius = sub ? SUB_AOE_RADIUS : AOE_RADIUS;
        float damage = (float) (arrow.getBaseDamage()
                * (sub ? SUB_AOE_DAMAGE_MULTIPLIER : AOE_DAMAGE_MULTIPLIER));

        Vec3 center = event.getRayTraceResult().getLocation();
        Entity owner = arrow.getOwner();
        Entity direct = event.getRayTraceResult() instanceof EntityHitResult entityHit
                ? entityHit.getEntity()
                : null;

        AABB area = new AABB(center, center).inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != owner && e != direct && !FriendlyMobs.isProtected(e))) {
            double distance = Math.sqrt(target.distanceToSqr(center));
            if (distance > radius) continue;

            float falloff = (float) (damage * (1.0D - distance / radius * 0.3D));
            if (falloff <= 0.0F) continue;

            target.hurt(level.damageSources().arrow(arrow, owner), falloff);
            pullTowards(target, center);
        }

        if (direct instanceof LivingEntity living && living.isAlive() && !FriendlyMobs.isProtected(living)) {
            pullTowards(living, center);
        }

        if (level instanceof ServerLevel serverLevel) {
            int count = sub ? 8 : 24;
            serverLevel.sendParticles(ParticleTypes.SNOWFLAKE,
                    center.x, center.y, center.z, count, radius * 0.4D, radius * 0.3D, radius * 0.4D, 0.02D);
        }
    }

    private static void pullTowards(LivingEntity target, Vec3 center) {
        Vec3 offset = center.subtract(target.position());
        if (offset.lengthSqr() < 1.0E-4D) return;

        Vec3 pull = offset.normalize().scale(PULL_STRENGTH);
        target.setDeltaMovement(target.getDeltaMovement().add(pull.x, pull.y + 0.1D, pull.z));
        target.hurtMarked = true;
    }
}
