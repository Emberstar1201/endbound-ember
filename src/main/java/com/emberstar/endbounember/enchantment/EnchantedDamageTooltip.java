package com.emberstar.endbounember.enchantment;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 附魔伤害预览：把附魔提供的常驻攻击伤害折算进悬停面板上的攻击伤害数值。
 *
 * <p>原版面板上的攻击伤害只统计物品属性修饰符，锋利这类 {@code minecraft:damage} 附魔效果
 * 不会体现在数字里，玩家因此看不出附魔有没有生效。这里按照附魔数据里定义的数值效果重新算一遍，
 * 算法与 {@code EnchantmentHelper#modifyDamage} 一致，只统计无条件的加伤。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class EnchantedDamageTooltip {
    /** 与原版属性面板一致的数值格式。 */
    private static final DecimalFormat FORMAT =
            new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private EnchantedDamageTooltip() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        if (player == null) return;

        ItemStack stack = event.getItemStack();
        float bonus = unconditionalDamageBonus(stack);
        if (bonus <= 0.0F) return;

        String descriptionId = Attributes.ATTACK_DAMAGE.value().getDescriptionId();
        String name = Component.translatable(descriptionId).getString();
        List<Component> tooltip = event.getToolTip();
        for (int i = 0; i < tooltip.size(); i++) {
            if (!tooltip.get(i).getString().trim().endsWith(name)) continue;

            // 保留原版“总伤害”的展示方式，只把数值换成含附魔加伤后的结果。
            double total = attackDamageTotal(player, stack) + bonus;
            tooltip.set(i, CommonComponents.space()
                    .append(Component.translatable("attribute.modifier.equals.0",
                            FORMAT.format(total), Component.translatable(descriptionId)))
                    .withStyle(ChatFormatting.DARK_GREEN));
            return;
        }
    }

    /** 按原版属性结算顺序计算主手上的总攻击伤害（不含附魔加伤）。 */
    private static double attackDamageTotal(Player player, ItemStack stack) {
        double base = player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        List<AttributeModifier> added = new ArrayList<>();
        List<AttributeModifier> multipliedBase = new ArrayList<>();
        List<AttributeModifier> multipliedTotal = new ArrayList<>();
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (!attribute.is(Attributes.ATTACK_DAMAGE)) return;
            switch (modifier.operation()) {
                case ADD_VALUE -> added.add(modifier);
                case ADD_MULTIPLIED_BASE -> multipliedBase.add(modifier);
                case ADD_MULTIPLIED_TOTAL -> multipliedTotal.add(modifier);
            }
        });

        double value = base;
        for (AttributeModifier modifier : added) {
            value += modifier.amount();
        }
        for (AttributeModifier modifier : multipliedBase) {
            value += base * modifier.amount();
        }
        for (AttributeModifier modifier : multipliedTotal) {
            value *= 1.0D + modifier.amount();
        }
        return value;
    }

    /** 汇总物品上所有“无条件”的附魔加伤；亡灵杀手、节肢杀手这类条件加伤不计入面板常驻数值。 */
    private static float unconditionalDamageBonus(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) return 0.0F;

        RandomSource random = RandomSource.create(0L);
        float bonus = 0.0F;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            int level = entry.getIntValue();
            if (level <= 0) continue;
            for (ConditionalEffect<EnchantmentValueEffect> effect
                    : entry.getKey().value().getEffects(EnchantmentEffectComponents.DAMAGE)) {
                if (effect.requirements().isPresent()) continue;
                bonus = effect.effect().process(level, random, bonus);
            }
        }
        return bonus;
    }
}
