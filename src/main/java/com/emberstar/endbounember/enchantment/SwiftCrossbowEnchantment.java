package com.emberstar.endbounember.enchantment;

import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 迅捷之弩：弩专属，最高 IV 级。
 * 弩箭命中时追加一段无视护甲的魔法伤害（I +2 / II +4 / III +6 / IV +8），
 * 同时缩短装填时间（I -0.25s / II -0.5s / III -1.0s / IV 近乎瞬间）。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class SwiftCrossbowEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "swift_crossbow"));

    /** I ~ IV 级的附加魔法伤害。 */
    private static final float[] BONUS_DAMAGE = { 2.0F, 4.0F, 6.0F, 8.0F };
    /** I ~ IV 级装填时间减少的 tick 数（原版基础装填 25 tick）。 */
    private static final int[] CHARGE_REDUCTION_TICKS = { 5, 10, 20, 24 };

    /** 记录箭上附魔等级的标记。 */
    private static final String TAG_LEVEL = "EndboundEmberSwiftCrossbowLevel";
    /** 追加魔法伤害时的递归保护标记（打在目标身上）。 */
    private static final String TAG_APPLYING = "EndboundEmberSwiftCrossbowApplying";
    /** 小数装填进度累积键。 */
    private static final String TAG_PROGRESS = "EndboundEmberSwiftCrossbowProgress";

    private SwiftCrossbowEnchantment() {
    }

    /** 取主副手弩上的较大等级。 */
    private static int levelOfHands(LivingEntity shooter) {
        return Math.max(levelOf(shooter.getMainHandItem()), levelOf(shooter.getOffhandItem()));
    }

    /** 读取物品上的迅捷之弩等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof CrossbowItem)) return 0;

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

    /** 装填加速：按等级额外扣掉蓄力进度，缩短弩的装填时间。 */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        ItemStack stack = event.getItem();
        if (!(stack.getItem() instanceof CrossbowItem)) return;

        int level = levelOf(stack);
        if (level <= 0) return;

        int base = CrossbowItem.getChargeDuration(stack, player);
        if (base <= 1) return;
        if (player.getTicksUsingItem() >= base) return;
        if (event.getDuration() <= 1) return;

        int reduction = CHARGE_REDUCTION_TICKS[Math.min(level, CHARGE_REDUCTION_TICKS.length) - 1];
        int target = Math.max(1, base - reduction);
        double extraPerTick = (double) base / target - 1.0D;

        int skip = (int) extraPerTick;
        float fractional = (float) (extraPerTick - skip);
        float accumulated = player.getPersistentData().getFloat(TAG_PROGRESS) + fractional;
        if (accumulated >= 1.0F) {
            skip++;
            accumulated -= 1.0F;
        }
        player.getPersistentData().putFloat(TAG_PROGRESS, accumulated);

        if (skip <= 0) return;
        event.setDuration(Math.max(1, event.getDuration() - skip));
    }

    /** 弩箭生成：把射手弩上的附魔等级写入箭，避免飞行途中切换武器丢失效果。 */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;

        CompoundTag tag = arrow.getPersistentData();
        if (tag.contains(TAG_LEVEL)) return;

        Entity owner = arrow.getOwner();
        if (!(owner instanceof Player player)) return;

        int level = levelOfHands(player);
        if (level <= 0) return;

        tag.putInt(TAG_LEVEL, level);
    }

    /** 弩箭命中：追加一段独立的魔法伤害，无视护甲。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!(event.getSource().getDirectEntity() instanceof AbstractArrow arrow)) return;

        int level = arrow.getPersistentData().getInt(TAG_LEVEL);
        if (level <= 0) return;

        // 追加伤害会再次触发伤害事件，用目标标记防止递归
        if (target.getPersistentData().getBoolean(TAG_APPLYING)) return;

        float bonus = BONUS_DAMAGE[Math.min(level, BONUS_DAMAGE.length) - 1];
        if (bonus <= 0.0F) return;

        target.getPersistentData().putBoolean(TAG_APPLYING, true);
        try {
            target.hurt(target.damageSources().indirectMagic(arrow, arrow.getOwner()), bonus);
        } finally {
            target.getPersistentData().remove(TAG_APPLYING);
        }
    }
}
