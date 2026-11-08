package com.emberstar.endbounember.enchantment;

import java.util.ArrayList;
import java.util.List;

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

        for (EnchantmentInstance candidate : candidates) {
            Holder<Enchantment> holder = candidate.enchantment;
            if (existing.getLevel(holder) > 0) {
                result.add(candidate);
                continue;
            }
            if (existing.size() + additions >= max) break;
            boolean compatible = existing.keySet().stream()
                    .allMatch(existingHolder -> Enchantment.areCompatible(holder, existingHolder));
            if (!compatible) continue;
            result.add(candidate);
            additions++;
        }
        return result;
    }

}
