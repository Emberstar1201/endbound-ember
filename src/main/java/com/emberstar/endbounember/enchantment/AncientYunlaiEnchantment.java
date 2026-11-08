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
 * 古·云来弓法：弓专属宝藏附魔，最高 I 级。
 * 蓄力倍率 2.0（约 0.5 秒），箭矢飞行速度 ×3.5，箭矢基础伤害 ×3.5，
 * 并生成 END_ROD 飞行轨迹。具体逻辑见 {@link AncientYunlaiHandler}。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class AncientYunlaiEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "ancient_yunlai"));

    /** 旧版 Config.ancientYunLaiChargeMultiplier 默认值：2.0（约 0.5 秒蓄力）。 */
    public static final double CHARGE_MULTIPLIER = 2.0D;
    /** 旧版 Config.ancientYunLaiArrowSpeed 默认值：3.5 倍箭速。 */
    public static final double ARROW_SPEED_MULTIPLIER = 3.5D;
    /** 旧版 AncientYunLaiHandler：箭矢基础伤害同样按 3.5 倍放大。 */
    public static final double ARROW_BASE_DAMAGE_MULTIPLIER = 3.5D;
    /** 原版满弦蓄力 tick 数。 */
    public static final int FULL_CHARGE_TICKS = 20;

    private static final int TOOLTIP_COLOR = 0x8FD0C8;

    private AncientYunlaiEnchantment() {
    }

    /** 读取物品上的古·云来弓法等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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
        String enchantName = Component.translatable("enchantment.endbound_ember.ancient_yunlai").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }
        tooltip.add(insertAt, Component.translatable("endbound_ember.ancient_yunlai.desc")
                .withStyle(style -> style.withColor(TOOLTIP_COLOR)));
    }
}
