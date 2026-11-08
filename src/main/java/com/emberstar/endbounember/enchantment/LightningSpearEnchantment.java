package com.emberstar.endbounember.enchantment;

import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.compat.FriendlyMobs;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * 雷电之戟：三叉戟专属，最高 II 级。
 * 投掷三叉戟命中实体时在其位置召唤闪电：
 * I 级需要处于雨天，II 级任意天气、任意维度均可触发。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class LightningSpearEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "lightning_spear"));

    /** 记录三叉戟上附魔等级的标记。 */
    private static final String TAG_LEVEL = "EndboundEmberLightningSpearLevel";
    /** 闪电附带的伤害（纯视觉闪电不造成伤害，这里单独结算）。 */
    private static final float LIGHTNING_DAMAGE = 5.0F;

    private LightningSpearEnchantment() {
    }

    /** 取主副手三叉戟上的较大等级。 */
    private static int levelOfHands(LivingEntity shooter) {
        return Math.max(levelOf(shooter.getMainHandItem()), levelOf(shooter.getOffhandItem()));
    }

    /** 读取物品上的雷电之戟等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof TridentItem)) return 0;

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

    /** 三叉戟生成：把投掷者手持三叉戟的附魔等级写入投掷物。 */
    @SubscribeEvent
    public static void onTridentJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ThrownTrident trident)) return;

        CompoundTag tag = trident.getPersistentData();
        if (tag.contains(TAG_LEVEL)) return;

        Entity owner = trident.getOwner();
        if (!(owner instanceof LivingEntity shooter)) return;

        int level = levelOfHands(shooter);
        if (level <= 0) {
            // 兜底：三叉戟已从手中移除时，从投掷物保存的物品读取附魔
            level = levelOf(trident.getPickupItemStackOrigin());
        }
        if (level <= 0) return;

        tag.putInt(TAG_LEVEL, level);
    }

    /** 三叉戟命中实体：按等级与天气条件召唤闪电。 */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownTrident trident)) return;

        Level level = trident.level();
        if (level.isClientSide()) return;

        int enchantLevel = trident.getPersistentData().getInt(TAG_LEVEL);
        if (enchantLevel <= 0) return;

        HitResult hit = event.getRayTraceResult();
        if (hit.getType() != HitResult.Type.ENTITY || !(hit instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target) || !target.isAlive()) return;
        if (FriendlyMobs.isProtected(target)) return;

        // I 级只在雨天触发；II 级任意天气、任意维度均可
        if (enchantLevel < 2 && !level.isRainingAt(target.blockPosition())) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(serverLevel);
        if (bolt == null) return;

        bolt.moveTo(target.getX(), target.getY(), target.getZ());
        // 纯视觉闪电：保留雷电视听效果，避免点燃地形与误伤周围生物
        bolt.setVisualOnly(true);
        serverLevel.addFreshEntity(bolt);
        target.hurt(serverLevel.damageSources().lightningBolt(), LIGHTNING_DAMAGE);
    }
}
