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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 疾跑节能：护腿专属，最高 II 级。
 * 疾跑时抵消疾跑产生的额外疲劳值（I 级减半、II 级完全抵消），并额外提供抗击退。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class SprintEnduranceEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "sprint_endurance"));

    /** 对应旧版配置 sprintEndurance.baseOffsetPerTick（默认 0.03，每 tick 疲劳值）。 */
    private static final double BASE_OFFSET_PER_TICK = 0.03D;
    /** 对应旧版 SprintEnduranceHandler 中 I 级的抗击退加成（0.10）。 */
    private static final double KNOCKBACK_RESISTANCE_L1 = 0.10D;
    /** 对应旧版 SprintEnduranceHandler 中 II 级的抗击退加成（0.20）。 */
    private static final double KNOCKBACK_RESISTANCE_L2 = 0.20D;

    private static final ResourceLocation KNOCKBACK_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            EndboundEmber.MODID, "sprint_endurance_knockback");

    private SprintEnduranceEnchantment() {
    }

    /** 读取物品上的疾跑节能等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        int level = levelOf(player.getItemBySlot(EquipmentSlot.LEGS));
        boolean active = level > 0 && player.isSprinting();

        AttributeInstance knockbackResistance = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackResistance != null) {
            AttributeModifier existing = knockbackResistance.getModifier(KNOCKBACK_MODIFIER_ID);
            if (active) {
                double amount = level >= 2 ? KNOCKBACK_RESISTANCE_L2 : KNOCKBACK_RESISTANCE_L1;
                if (existing == null || existing.amount() != amount) {
                    if (existing != null) {
                        knockbackResistance.removeModifier(KNOCKBACK_MODIFIER_ID);
                    }
                    knockbackResistance.addTransientModifier(new AttributeModifier(
                            KNOCKBACK_MODIFIER_ID, amount, AttributeModifier.Operation.ADD_VALUE));
                }
            } else if (existing != null) {
                knockbackResistance.removeModifier(KNOCKBACK_MODIFIER_ID);
            }
        }

        if (!active) return;

        double reduction = level >= 2 ? BASE_OFFSET_PER_TICK : BASE_OFFSET_PER_TICK * 0.5D;
        FoodData foodData = player.getFoodData();
        foodData.setExhaustion(Math.max(0.0F, foodData.getExhaustionLevel() - (float) reduction));
    }
}
