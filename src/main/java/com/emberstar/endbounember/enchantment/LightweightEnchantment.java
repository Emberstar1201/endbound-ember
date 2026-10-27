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
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 轻便：重锤专属，最高 III 级。
 * 手持重锤时提升攻击速度，让笨重的重锤挥动得更快。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class LightweightEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "lightweight"));

    private static final ResourceLocation ATTACK_SPEED_ID = modifierId("lightweight_attack_speed");
    private static final double[] ATTACK_SPEED_BONUS = {0.0D, 0.2D, 0.4D, 0.6D};

    private static final int TOOLTIP_COLOR = 0xC9A66B;

    private LightweightEnchantment() {
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, path);
    }

    /** 读取物品上的轻便附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 每 tick 维护攻速修饰符，仅在主手持有重锤时生效。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack mainHand = player.getMainHandItem();
        int level = levelOf(mainHand);
        if (level > 3) level = 3;
        boolean active = level > 0 && mainHand.is(Items.MACE);

        manageModifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID,
                active ? ATTACK_SPEED_BONUS[level] : 0.0D, active);
    }

    /** 需要时添加 / 更新修饰符，不需要时移除。 */
    private static void manageModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id,
            double value, boolean shouldHave) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        AttributeModifier existing = instance.getModifier(id);
        if (!shouldHave) {
            if (existing != null) instance.removeModifier(id);
            return;
        }
        if (existing != null && existing.amount() == value) return;
        if (existing != null) instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
    }

    /** 物品提示：在附魔名称之后追加攻速说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int level = levelOf(stack);
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.lightweight").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.lightweight.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.lightweight.speed",
                String.format(Locale.ROOT, "%.1f", ATTACK_SPEED_BONUS[Math.min(level, 3)])));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
