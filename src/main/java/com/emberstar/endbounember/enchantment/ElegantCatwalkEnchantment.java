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
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 优雅猫步：靴子专属，最高 I 级。
 * 穿着时移动更快，并且免疫摔落伤害，如同猫一般轻盈。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class ElegantCatwalkEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "elegant_catwalk"));

    private static final ResourceLocation SPEED_ID = modifierId("elegant_catwalk_speed");

    private static final double SPEED_MULTIPLIER = 1.45D;

    private static final int TOOLTIP_COLOR = 0xC9A66B;

    private ElegantCatwalkEnchantment() {
    }

    private static ResourceLocation modifierId(String path) {
        return ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, path);
    }

    /** 读取物品上的优雅猫步附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 每 tick 维护移速修饰符，仅在正常行走状态下生效。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        int level = levelOf(player.getItemBySlot(EquipmentSlot.FEET));
        boolean active = level > 0 && !player.isFallFlying() && !player.isPassenger()
                && !player.getAbilities().flying;

        // 属性修饰符只在服务端维护，客户端由同步的属性值获得结果
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            manageSpeedModifier(serverPlayer, active);
        }
    }

    /** 穿着附魔靴子时完全免疫摔落伤害。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.getSource().is(DamageTypes.FALL)) return;
        if (levelOf(player.getItemBySlot(EquipmentSlot.FEET)) <= 0) return;

        player.fallDistance = 0.0F;
        event.setCanceled(true);
    }

    /** 按固定倍率维护移速修饰符。 */
    private static void manageSpeedModifier(ServerPlayer player, boolean active) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) return;

        AttributeModifier existing = instance.getModifier(SPEED_ID);
        if (!active) {
            if (existing != null) instance.removeModifier(SPEED_ID);
            return;
        }

        double amount = SPEED_MULTIPLIER - 1.0D;
        if (existing != null && existing.amount() == amount) return;
        if (existing != null) instance.removeModifier(SPEED_ID);
        instance.addTransientModifier(
                new AttributeModifier(SPEED_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /** 物品提示：在附魔名称之后追加移速与免摔落说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        int level = levelOf(stack);
        if (level <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.elegant_catwalk").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }

        tooltip.add(insertAt, line("endbound_ember.elegant_catwalk.desc"));
        tooltip.add(insertAt + 1, line("endbound_ember.elegant_catwalk.speed",
                String.format(Locale.ROOT, "%.0f", SPEED_MULTIPLIER * 100.0D)));
        tooltip.add(insertAt + 2, line("endbound_ember.elegant_catwalk.fall"));
    }

    private static Component line(String key, Object... args) {
        return Component.translatable(key, args).withStyle(style -> style.withColor(TOOLTIP_COLOR));
    }
}
