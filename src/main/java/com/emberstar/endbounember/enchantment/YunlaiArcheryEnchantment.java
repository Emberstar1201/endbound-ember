package com.emberstar.endbounember.enchantment;

import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * 云来弓法：弓专属，最高 I 级。
 * 拉弓蓄力压缩到约 0.45 秒（9 tick），并小幅提升箭矢速度与基础伤害。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class YunlaiArcheryEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "yunlai_archery"));

    /** 原版满蓄力 20 tick（1.0 秒）。 */
    private static final int BASE_DRAW_TICKS = 20;
    /** 附魔后满蓄力 9 tick（约 0.45 秒）。 */
    private static final int ENCHANTED_DRAW_TICKS = 9;
    /** 箭矢出膛速度倍率。 */
    private static final double ARROW_SPEED_MULTIPLIER = 1.15D;
    /** 箭矢基础伤害加成。 */
    private static final double ARROW_DAMAGE_BONUS = 1.0D;
    /** 小数蓄力进度累积键，避免每 tick 多跳的进度无法整除而丢失。 */
    private static final String TAG_PROGRESS = "EndboundEmberYunlaiArcheryProgress";

    private YunlaiArcheryEnchantment() {
    }

    /** 取主副手弓上的较大等级。 */
    private static int levelOfHands(LivingEntity shooter) {
        return Math.max(levelOf(shooter.getMainHandItem()), levelOf(shooter.getOffhandItem()));
    }

    /** 读取物品上的云来弓法等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
    public static int levelOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (!(stack.getItem() instanceof BowItem)) return 0;

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

    /** 拉弓加速：每 tick 额外扣掉部分蓄力进度，把满蓄力从 20 tick 压缩到 9 tick。 */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        ItemStack stack = event.getItem();
        if (!(stack.getItem() instanceof BowItem)) return;
        if (levelOf(stack) <= 0) return;

        // 已满蓄力或即将结束使用，交给原版结算
        if (player.getTicksUsingItem() >= BASE_DRAW_TICKS) return;
        if (event.getDuration() <= 1) return;

        double extraPerTick = (double) BASE_DRAW_TICKS / ENCHANTED_DRAW_TICKS - 1.0D;
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

    /** 箭矢生成：提升带云来弓法的箭矢出膛速度与基础伤害。 */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (arrow.tickCount > 0) return;

        Entity owner = arrow.getOwner();
        if (!(owner instanceof Player player)) return;
        if (levelOfHands(player) <= 0) return;

        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(ARROW_SPEED_MULTIPLIER));
        arrow.setBaseDamage(arrow.getBaseDamage() + ARROW_DAMAGE_BONUS);
    }
}
