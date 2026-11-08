package com.emberstar.endbounember.enchantment;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 古·云来剑法：剑类宝藏附魔，最高 I 级。
 * 手持时攻击速度 +5.0、攻击距离 +5.0 格、近战伤害 ×1.50，
 * 命中时 75% 概率向视线方向斩出射程 10 格的剑气（伤害为本次攻击的 75%）。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class AncientYunlaiSwordmanshipEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "ancient_yunlai_swordmanship"));

    /** 旧版 Config.ancientYunlaiSwordmanship.attackSpeed 默认值。 */
    public static final double ATTACK_SPEED_BONUS = 5.0D;
    /** 旧版 Config.ancientYunlaiSwordmanship.attackReach 默认值。 */
    public static final double ATTACK_REACH_BONUS = 5.0D;
    /** 旧版 Config.ancientYunlaiSwordmanship.damageMultiplier 默认值。 */
    public static final double DAMAGE_MULTIPLIER = 1.50D;
    /** 旧版 Config.ancientYunlaiSwordmanship.swordQiChance 默认值。 */
    public static final double SWORD_QI_CHANCE = 0.75D;
    /** 旧版 Config.ancientYunlaiSwordmanship.swordQiDamageMultiplier 默认值。 */
    public static final double SWORD_QI_DAMAGE_MULTIPLIER = 0.75D;
    /** 旧版 Config.ancientYunlaiSwordmanship.swordQiRange 默认值。 */
    public static final double SWORD_QI_RANGE = 10.0D;

    private static final int TOOLTIP_COLOR = 0xD9B23C;

    private AncientYunlaiSwordmanshipEnchantment() {
    }

    /** 读取物品上的古·云来剑法等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 物品提示：在附魔名称之后追加效果说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (levelOf(stack) <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable(
                "enchantment.endbound_ember.ancient_yunlai_swordmanship").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }
        tooltip.add(insertAt, Component.translatable("endbound_ember.ancient_yunlai_swordmanship.desc")
                .withStyle(style -> style.withColor(TOOLTIP_COLOR)));
    }
}
