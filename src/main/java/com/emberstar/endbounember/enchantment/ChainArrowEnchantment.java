package com.emberstar.endbounember.enchantment;

import java.util.Comparator;
import java.util.List;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * 贯穿链条：弓 / 弩专属，最高 III 级。
 * 箭矢命中实体后按等级在搜索半径内弹射最近的敌人，每次弹射造成箭基础伤害的 80%；
 * 与爆破箭矢互斥。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ChainArrowEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "chain_arrow"));

    /** 旧版 ChainArrowConfig.bounceCountLevel1 / 2 / 3 默认值（1 / 2 / 3 次弹射）。 */
    private static final int[] BOUNCE_COUNT = { 1, 2, 3 };
    /** 旧版 ChainArrowConfig.damageRatio 默认值：每次弹射伤害占箭基础伤害的 80%。 */
    private static final float DAMAGE_RATIO = 0.8F;
    /** 旧版 ChainArrowConfig.searchRange 默认值：寻找下一目标的搜索半径（格）。 */
    private static final double SEARCH_RANGE = 10.0D;

    /** 旧版 ChainArrowHandler.TAG_CHAIN_LEVEL：记录箭上的附魔等级。 */
    private static final String TAG_LEVEL = "EndboundEmberChainArrowLevel";

    private ChainArrowEnchantment() {
    }

    /** 旧版 ChainArrowHandler.getChainLevel：取主副手弓 / 弩上的较大等级。 */
    private static int levelOfHands(LivingEntity shooter) {
        return Math.max(levelOf(shooter.getMainHandItem()), levelOf(shooter.getOffhandItem()));
    }

    /** 读取物品上的贯穿链条附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 箭矢命中实体：先结算首目标，再逐跳弹射，随后移除箭矢。 */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) return;

        Level level = arrow.level();
        if (level.isClientSide()) return;

        int enchantLevel = arrow.getPersistentData().getInt(TAG_LEVEL);
        if (enchantLevel <= 0) return;

        HitResult hit = event.getRayTraceResult();
        if (!(hit instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity firstTarget)) return;

        int index = Math.min(Math.max(enchantLevel, 1), BOUNCE_COUNT.length) - 1;
        int bounceCount = BOUNCE_COUNT[index];
        float bounceDamage = (float) (arrow.getBaseDamage() * DAMAGE_RATIO);

        Entity owner = arrow.getOwner();
        firstTarget.hurt(level.damageSources().arrow(arrow, owner), bounceDamage);

        LivingEntity current = firstTarget;
        for (int i = 0; i < bounceCount; i++) {
            LivingEntity next = findNextTarget(level, current, owner);
            if (next == null) break;

            next.hurt(level.damageSources().arrow(arrow, owner), bounceDamage);

            double centerY = next.getY() + next.getBbHeight() * 0.5D;
            arrow.moveTo(next.getX(), centerY, next.getZ());
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        next.getX(), centerY, next.getZ(), 3, 0.2D, 0.2D, 0.2D, 0.02D);
            }

            current = next;
        }

        arrow.discard();
        event.setCanceled(true);
    }

    /** 在搜索半径内寻找距当前目标最近的下一目标，排除友方。 */
    private static LivingEntity findNextTarget(Level level, LivingEntity current, Entity owner) {
        AABB area = current.getBoundingBox().inflate(SEARCH_RANGE);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != current && e != owner && !FriendlyMobs.isProtected(e));
        return candidates.stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(current)))
                .orElse(null);
    }
}
