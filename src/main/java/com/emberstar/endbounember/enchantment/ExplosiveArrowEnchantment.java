package com.emberstar.endbounember.enchantment;

import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.compat.FriendlyMobs;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * 爆破箭矢：弓 / 弩专属，最高 III 级。
 * 箭矢进入世界时记录附魔等级，命中后引发不破坏地形的小型爆炸；
 * 被直接命中的目标承受全额中心伤害，周围生物按距离衰减；与贯穿链条互斥。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ExplosiveArrowEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "explosive_arrow"));

    /** 旧版 ExplosiveArrowConfig.radiusLevel1 / radiusLevel2 / radiusLevel3 默认值（2 / 3 / 4 格）。 */
    private static final double[] EXPLOSION_RADIUS = { 2.0D, 3.0D, 4.0D };
    /** 旧版 ExplosiveArrowConfig.damageLevel1 / damageLevel2 / damageLevel3 默认值（6 / 9 / 12）。 */
    private static final double[] EXPLOSION_DAMAGE = { 6.0D, 9.0D, 12.0D };

    /** 旧版 ExplosiveArrowHandler.TAG_EXPLOSIVE_LEVEL：记录箭上的附魔等级。 */
    private static final String TAG_LEVEL = "EndboundEmberExplosiveArrowLevel";

    private ExplosiveArrowEnchantment() {
    }

    /** 旧版 ExplosiveArrowHandler.getExplosiveLevel：取主副手弓 / 弩上的较大等级。 */
    private static int levelOfHands(LivingEntity shooter) {
        return Math.max(levelOf(shooter.getMainHandItem()), levelOf(shooter.getOffhandItem()));
    }

    /** 读取物品上的爆破箭矢附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof BowItem) && !(stack.getItem() instanceof CrossbowItem)) return 0;

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

    /** 箭矢生成：把射手弓 / 弩上的附魔等级写入箭，避免箭飞行期间切换武器丢失效果。 */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        CompoundTag tag = arrow.getPersistentData();
        if (tag.contains(TAG_LEVEL)) return;
        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;

        int level = levelOfHands(shooter);
        if (level <= 0) return;

        tag.putInt(TAG_LEVEL, level);
    }

    /** 箭矢命中：对命中目标与范围内生物结算爆炸伤害，随后移除箭矢。 */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;

        Level level = arrow.level();
        if (level.isClientSide()) return;

        int enchantLevel = arrow.getPersistentData().getInt(TAG_LEVEL);
        if (enchantLevel <= 0) return;

        int index = Math.min(Math.max(enchantLevel, 1), EXPLOSION_RADIUS.length) - 1;
        double radius = EXPLOSION_RADIUS[index];
        double maxDamage = EXPLOSION_DAMAGE[index];

        HitResult hit = event.getRayTraceResult();
        Vec3 hitPos = hit.getLocation();
        Entity owner = arrow.getOwner();

        LivingEntity directTarget = null;
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof LivingEntity target && target.isAlive()) {
                directTarget = target;
                target.hurt(level.damageSources().generic(), (float) maxDamage);
            }
        }

        LivingEntity finalDirectTarget = directTarget;
        AABB area = new AABB(hitPos.x - radius, hitPos.y - radius, hitPos.z - radius,
                hitPos.x + radius, hitPos.y + radius, hitPos.z + radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != owner && e != finalDirectTarget && !FriendlyMobs.isProtected(e))) {
            double distance = Math.sqrt(entity.distanceToSqr(hitPos));
            if (distance > radius) continue;

            float falloff = (float) (maxDamage * (1.0D - distance / radius));
            if (falloff <= 0.0F) continue;
            entity.hurt(level.damageSources().explosion(owner, owner), falloff);
        }

        level.playSound(null, hitPos.x, hitPos.y, hitPos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION,
                    hitPos.x, hitPos.y, hitPos.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        arrow.discard();
        event.setCanceled(true);
    }
}
