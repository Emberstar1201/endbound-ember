package com.emberstar.endbounember.enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 攫取：近战武器附魔，最高 V 级。
 * 攻击时按概率打落目标主手武器；再以一半概率独立打落一件护甲。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class SnatchEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "snatch"));

    /** 旧版 SnatchEnchantment.TRIGGER_CHANCES：武器缴械概率 I~V。 */
    private static final double[] TRIGGER_CHANCES = { 0.10D, 0.20D, 0.40D, 0.60D, 0.80D };
    private static final int MAX_LEVEL = 5;
    private static final int TOOLTIP_COLOR = 0x9A5B33;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private SnatchEnchantment() {
    }

    /** 读取物品上的攫取附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 攻击命中：先判定打落武器，再独立判定打落一件护甲。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (event.getSource().getDirectEntity() != attacker) return;
        if (target == attacker) return;

        int snatchLevel = levelOf(attacker.getMainHandItem());
        if (snatchLevel <= 0) return;

        double weaponChance = triggerChance(snatchLevel);
        double armorChance = armorTriggerChance(snatchLevel);

        // 武器缴械
        ItemStack mainHandStack = target.getMainHandItem();
        if (!mainHandStack.isEmpty() && target.level().random.nextDouble() < weaponChance) {
            target.spawnAtLocation(mainHandStack.copy());
            target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        // 护甲缴械：先从非空护甲槽中随机选一件
        List<EquipmentSlot> nonEmptyArmorSlots = new ArrayList<>();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!target.getItemBySlot(slot).isEmpty()) nonEmptyArmorSlots.add(slot);
        }
        if (!nonEmptyArmorSlots.isEmpty() && target.level().random.nextDouble() < armorChance) {
            EquipmentSlot chosenSlot = nonEmptyArmorSlots.get(
                    target.level().random.nextInt(nonEmptyArmorSlots.size()));
            ItemStack armorStack = target.getItemBySlot(chosenSlot);
            target.spawnAtLocation(armorStack.copy());
            target.setItemSlot(chosenSlot, ItemStack.EMPTY);
        }
    }

    private static int clamped(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    /** 旧版 SnatchEnchantment.getTriggerChance。 */
    public static double triggerChance(int level) {
        return TRIGGER_CHANCES[clamped(level) - 1];
    }

    /** 旧版 SnatchEnchantment.getArmorTriggerChance：护甲概率为武器的一半。 */
    public static double armorTriggerChance(int level) {
        return triggerChance(level) / 2.0D;
    }

    /** 物品提示：在附魔名称之后追加效果说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (levelOf(stack) <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.snatch").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }
        tooltip.add(insertAt, Component.translatable("endbound_ember.snatch.desc")
                .withStyle(style -> style.withColor(TOOLTIP_COLOR)));
    }
}
