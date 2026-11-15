package com.emberstar.endbounember.enchantment;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 通用附魔：让原本只对特定物品生效的附魔在其它物品上也能发挥作用。
 * 目前包含「效率」作用于剑等非工具物品，以及「快速装填」作用于弓。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class UniversalEnchantments {
    /** 快速装填每级等效缩短的拉弓时间（tick）。 */
    private static final int QUICK_CHARGE_TICKS_PER_LEVEL = 4;

    private UniversalEnchantments() {
    }

    /**
     * 效率：原版只在工具自身挖掘速度大于 1.0 时才叠加挖掘效率属性，
     * 剑等物品的基础速度恰好是 1.0，因此永远拿不到这份加成，这里补上。
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        // 与 Player#getDigSpeed 的判定保持一致：工具路径已由原版加过，避免重复计算
        if (player.getInventory().getDestroySpeed(event.getState()) > 1.0F) return;

        double efficiency = player.getAttributeValue(Attributes.MINING_EFFICIENCY);
        if (efficiency <= 0.0D) return;
        event.setNewSpeed(event.getNewSpeed() + (float) efficiency);
    }

    /** 快速装填：原版只作用于弩，这里对弓按等级缩短等效拉弓时间（拉满 20 tick）。 */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        ItemStack bow = event.getBow();
        if (!(bow.getItem() instanceof BowItem)) return;

        int level = quickChargeLevel(bow);
        if (level <= 0) return;

        event.setCharge(event.getCharge() + QUICK_CHARGE_TICKS_PER_LEVEL * level);
    }

    private static int quickChargeLevel(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            if (entry.getKey().is(Enchantments.QUICK_CHARGE)) return entry.getIntValue();
        }
        return 0;
    }
}
