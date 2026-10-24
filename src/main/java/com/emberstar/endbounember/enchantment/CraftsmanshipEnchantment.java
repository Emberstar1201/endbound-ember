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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * 匠心传承：单一等级。
 * 每破坏 10 个方块永久提升该工具 25% 挖掘速度（上限 +250%）；
 * 手持该工具时免疫原版的空中挖掘惩罚与水中挖掘惩罚。
 * 累计方块数存放在独立的 {EndboundEmber#CRAFTSMANSHIP_COUNT} 组件里，附魔等级固定为 1 级。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class CraftsmanshipEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "craftsmanship"));

    /** 每破坏多少个方块提升一档挖掘速度。 */
    public static final int BLOCKS_PER_STEP = 10;
    /** 每一档的挖掘速度加成比例（25%）。 */
    public static final double BONUS_PER_STEP = 0.25D;
    /** 挖掘速度加成上限（+250%）。 */
    public static final double MAX_BONUS = 2.5D;

    /** 原版空中挖掘惩罚的除数，免疫时按同样的倍数补回。 */
    private static final float PENALTY_DIVISOR = 5.0F;
    private static final int TOOLTIP_COLOR = 0xFFD08A;

    private CraftsmanshipEnchantment() {
    }

    /** 读取物品上的匠心传承附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 该工具累计破坏的方块数。 */
    public static int countOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Integer count = stack.get(EndboundEmber.CRAFTSMANSHIP_COUNT.get());
        return count == null ? 0 : Math.max(0, count);
    }

    /** 当前方块数对应的挖掘速度加成比例，每满 100 个 +5%，最高 +100%。 */
    public static double bonus(int count) {
        return Math.min(MAX_BONUS, (count / BLOCKS_PER_STEP) * BONUS_PER_STEP);
    }

    /** 破坏方块时累加计数，每满一档在物品栏上方提示一次。 */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide()) return;

        ItemStack tool = player.getMainHandItem();
        if (levelOf(tool) <= 0) return;

        int count = countOf(tool) + 1;
        tool.set(EndboundEmber.CRAFTSMANSHIP_COUNT.get(), count);

        if (count % BLOCKS_PER_STEP == 0) {
            player.displayClientMessage(Component.translatable("endbound_ember.craftsmanship.grow",
                    String.format(java.util.Locale.ROOT, "%.0f", bonus(count) * 100.0D)), true);
        }
    }

    /**
     * 挖掘速度修正。
     * 该事件在原版算完水中惩罚与空中惩罚之后触发，这里把惩罚还原即可免疫；
     * 之后再乘上永久成长带来的加成。
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ItemStack tool = player.getMainHandItem();
        if (levelOf(tool) <= 0) return;

        float speed = event.getNewSpeed();
        // 水中惩罚：原版在水下会乘以「水下挖掘速度」属性（默认 0.2，水下速掘会把它提到 1.0），除以该值即可还原
        if (player.isEyeInFluid(FluidTags.WATER)) {
            double submerged = player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
            if (submerged > 0.0D) {
                speed /= (float) submerged;
            }
        }
        // 空中惩罚：原版在离地时除以 5
        if (!player.onGround()) {
            speed *= PENALTY_DIVISOR;
        }
        event.setNewSpeed((float) (speed * (1.0D + bonus(countOf(tool)))));
    }

    /** 物品提示：在附魔名称之后追加累计方块数与当前加成。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (levelOf(stack) <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.craftsmanship").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        int count = countOf(stack);
        tooltip.add(insertAt, line("endbound_ember.craftsmanship.blocks", String.valueOf(count),
                String.valueOf(BLOCKS_PER_STEP),
                String.format(java.util.Locale.ROOT, "%.0f", BONUS_PER_STEP * 100.0D)));
        tooltip.add(insertAt + 1, line("endbound_ember.craftsmanship.speed",
                String.format(java.util.Locale.ROOT, "%.0f", bonus(count) * 100.0D),
                String.format(java.util.Locale.ROOT, "%.0f", MAX_BONUS * 100.0D)));
        tooltip.add(insertAt + 2, line("endbound_ember.craftsmanship.penalty"));
    }

    private static Component line(String key, String... args) {
        return Component.translatable(key, (Object[]) args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
