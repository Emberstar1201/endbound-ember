package com.emberstar.endbounember.enchantment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.emberstar.endbounember.Config;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class EnchantingTableRules {
    private EnchantingTableRules() {
    }

    public static boolean canUse(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.isEnchantable() || !stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }

    public static int enchantmentCount(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size();
    }

    public static List<EnchantmentInstance> limitCandidates(ItemStack stack, List<EnchantmentInstance> candidates) {
        if (candidates.isEmpty()) return candidates;

        ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int max = Config.ENCHANTING_TABLE_MAX_ENCHANTMENTS.get();
        List<EnchantmentInstance> result = new ArrayList<>(candidates.size());
        int additions = 0;

        Map<Holder<Enchantment>, EnchantmentInstance> uniqueCandidates = new LinkedHashMap<>();
        for (EnchantmentInstance candidate : candidates) {
            uniqueCandidates.merge(candidate.enchantment, candidate,
                    (first, second) -> first.level >= second.level ? first : second);
        }

        for (EnchantmentInstance candidate : uniqueCandidates.values()) {
            Holder<Enchantment> holder = candidate.enchantment;
            int existingLevel = existing.getLevel(holder);
            if (existingLevel > 0) {
                if (candidate.level > existingLevel) result.add(candidate);
                continue;
            }
            if (existing.size() + additions >= max) continue;
            boolean compatible = existing.keySet().stream()
                    .allMatch(existingHolder -> Enchantment.areCompatible(holder, existingHolder))
                    && result.stream().allMatch(accepted -> Enchantment.areCompatible(holder, accepted.enchantment));
            if (!compatible) continue;
            result.add(candidate);
            additions++;
        }
        return result;
    }

}
