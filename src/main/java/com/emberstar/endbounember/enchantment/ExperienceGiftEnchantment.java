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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

/**
 * 经验馈赠：武器（剑 / 斧 / 三叉戟 / 弓 / 弩）专属，最高 I 级。
 * 使用该武器击杀怪物时，额外掉落该怪物原本经验值 50% 的经验（向下取整，至少 +1）。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ExperienceGiftEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "experience_gift"));

    /** 额外经验倍率（50%）。 */
    private static final double BONUS_RATIO = 0.5D;

    private ExperienceGiftEnchantment() {
    }

    /** 读取物品上的经验馈赠等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 击杀怪物时按原版经验掉落量追加 50%，至少额外 +1 点。 */
    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        Player killer = event.getAttackingPlayer();
        if (killer == null || killer.level().isClientSide()) return;

        int level = Math.max(levelOf(killer.getMainHandItem()), levelOf(killer.getOffhandItem()));
        if (level <= 0) return;

        int original = event.getDroppedExperience();
        if (original <= 0) return;

        int bonus = Math.max(1, (int) (original * BONUS_RATIO));
        event.setDroppedExperience(original + bonus);
    }
}
