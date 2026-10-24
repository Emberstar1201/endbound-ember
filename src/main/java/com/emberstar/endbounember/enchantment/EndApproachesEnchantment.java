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
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 终末将至：单一等级，只能从要塞的宝箱中获得。
 * 这是针对强化末影龙的专用附魔，不提供通用攻速、破甲或残血增伤。
 * 持有者攻击末影龙时，最多压低 3 颗末影水晶的共鸣减伤。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class EndApproachesEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "end_approaches"));

    /** 最多抵消的水晶数量，等价于最多降低 24 个百分点的水晶减伤。 */
    public static final int CRYSTAL_WARD_BREAK = 3;

    private static final int TOOLTIP_COLOR = 0xC9A6FF;

    private EndApproachesEnchantment() {
    }

    /** 读取物品上的终末将至附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 终末将至只对持有者当前攻击造成的水晶共鸣生效，不改变其它目标的伤害。 */
    public static int effectiveCrystalCount(EnderDragon dragon, DamageSource source, int crystals) {
        if (crystals <= 0) return 0;
        if (!(source.getEntity() instanceof Player player)) return crystals;
        if (source.getDirectEntity() != player) return crystals;
        if (levelOf(player.getMainHandItem()) <= 0) return crystals;
        return Math.max(0, crystals - CRYSTAL_WARD_BREAK);
    }

    /** 物品提示：在附魔名称之后追加专用效果说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (levelOf(stack) <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.end_approaches").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.end_approaches.ward", String.valueOf(CRYSTAL_WARD_BREAK)));
        tooltip.add(insertAt + 1, line("endbound_ember.end_approaches.loot"));
    }

    private static Component line(String key, String... args) {
        return Component.translatable(key, (Object[]) args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
