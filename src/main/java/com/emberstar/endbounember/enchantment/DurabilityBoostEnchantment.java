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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

/**
 * 耐久强化：直接提高物品的最大耐久。
 * 1 级 +150%、2 级 +200%、3 级 +400%，其中 1、2 级可以由附魔台附魔，3 级只能从宝箱中找到。
 * 最大耐久在读取时按附魔等级放大，物品本身只保留原版的基础耐久值。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class DurabilityBoostEnchantment {
    public static final ResourceKey<Enchantment> DURABILITY_BOOST = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "durability_boost"));

    /** 各等级对基础耐久的提升比例，索引即等级：1 级 +150%、2 级 +200%、3 级 +400%。 */
    private static final double[] BONUS = {0.0D, 1.5D, 2.0D, 4.0D};

    private DurabilityBoostEnchantment() {
    }

    /** 读取物品上的耐久强化等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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
            if (key.isPresent() && key.get().equals(DURABILITY_BOOST)) return entry.getIntValue();
        }
        return 0;
    }

    /** 按等级放大最大耐久，基础耐久取自物品数据组件，不会被改写。 */
    public static int boostedMaxDamage(int baseMaxDamage, int level) {
        if (baseMaxDamage <= 0 || level <= 0) return baseMaxDamage;
        double bonus = BONUS[Math.min(level, BONUS.length - 1)];
        return baseMaxDamage + (int) Math.round(baseMaxDamage * bonus);
    }

    /** 3 级只能从宝箱获得，阻止铁砧用两本 2 级书合成出 3 级。 */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (levelOf(event.getLeft()) < 2 || levelOf(event.getRight()) < 2) return;
        event.setCanceled(true);
    }
}
