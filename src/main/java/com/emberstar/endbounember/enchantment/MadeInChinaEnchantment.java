package com.emberstar.endbounember.enchantment;

import java.util.List;
import java.util.Locale;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 中国制造：单一等级，集「耐久强化 + 原版耐久 + 匠心传承」于一身。
 * ① 最大耐久翻倍（由 ItemStackMaxDamageMixin 生效）；
 * ② 每次损耗耐久有 60% 概率完全免除（由 ItemStackMadeInChinaMixin 生效）；
 * ③ 装备在身上时每 3 秒自动恢复 1 点耐久；
 * ④ 镐 / 斧 / 锹额外获得匠心传承的成长与惩罚免疫（见 CraftsmanshipEnchantment）。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class MadeInChinaEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "made_in_china"));

    /** 最大耐久倍率（百分比），200 即 ×2.0。 */
    public static final int MAX_DURABILITY_PERCENT = 200;
    /** 每次损耗耐久时免除的概率（60%）。 */
    public static final double IGNORE_DURABILITY_CHANCE = 0.60D;
    /** 自动修复间隔（60 tick = 3 秒）。 */
    public static final int AUTO_REPAIR_INTERVAL_TICKS = 60;
    /** 每次自动修复恢复的耐久值。 */
    public static final int AUTO_REPAIR_AMOUNT = 1;

    /** 自动修复覆盖的槽位：主手、副手与四件盔甲。 */
    private static final EquipmentSlot[] REPAIR_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int TOOLTIP_COLOR = 0xFFD08A;

    private MadeInChinaEnchantment() {
    }

    /** 读取物品上的中国制造附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 附魔后的最大耐久。 */
    public static int boostedMaxDamage(int baseMaxDamage) {
        return baseMaxDamage * MAX_DURABILITY_PERCENT / 100;
    }

    /** 该物品本次损耗耐久是否被完全免除。 */
    public static boolean ignoresDurability(ItemStack stack, RandomSource random) {
        return levelOf(stack) > 0 && random.nextDouble() < IGNORE_DURABILITY_CHANCE;
    }

    /** 每 3 秒为身上带该附魔的装备恢复 1 点耐久。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % AUTO_REPAIR_INTERVAL_TICKS != 0) return;

        for (EquipmentSlot slot : REPAIR_SLOTS) {
            repair(player.getItemBySlot(slot));
        }
    }

    private static void repair(ItemStack stack) {
        if (levelOf(stack) <= 0) return;
        int damage = stack.getDamageValue();
        if (damage <= 0) return;
        stack.setDamageValue(Math.max(0, damage - AUTO_REPAIR_AMOUNT));
    }

    /** 物品提示：在附魔名称之后追加四重机制说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (levelOf(stack) <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.made_in_china").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.made_in_china.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.made_in_china.durability",
                String.valueOf(MAX_DURABILITY_PERCENT / 100)));
        tooltip.add(insertAt + 2, line("endbound_ember.made_in_china.ignore",
                String.format(Locale.ROOT, "%.0f", IGNORE_DURABILITY_CHANCE * 100.0D)));
        tooltip.add(insertAt + 3, line("endbound_ember.made_in_china.repair",
                String.valueOf(AUTO_REPAIR_INTERVAL_TICKS / 20),
                String.valueOf(AUTO_REPAIR_AMOUNT)));
        tooltip.add(insertAt + 4, line("endbound_ember.made_in_china.craftsmanship"));
    }

    private static Component line(String key, String... args) {
        return Component.translatable(key, (Object[]) args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
