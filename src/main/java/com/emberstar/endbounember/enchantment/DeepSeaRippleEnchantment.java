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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 深海的涟漪：靴子专属，最高 I 级。
 * 在水中潜行时获得额外游泳速度，离开水面或松开潜行后立即恢复原速。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class DeepSeaRippleEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "deep_sea_ripple"));

    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID,
            "deep_sea_ripple_speed");

    /** 水中潜行时的移速倍率。 */
    private static final double WATER_MULTIPLIER = 1.6D;

    private DeepSeaRippleEnchantment() {
    }

    /** 读取物品上的深海的涟漪等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        boolean active = levelOf(player.getItemBySlot(EquipmentSlot.FEET)) > 0
                && player.isInWater()
                && player.isShiftKeyDown()
                && !player.isPassenger();

        manageSpeedModifier(serverPlayer, active ? WATER_MULTIPLIER : 1.0D);
    }

    /** 按目标倍率维护移速修饰符，倍率为 1 时移除。 */
    private static void manageSpeedModifier(ServerPlayer player, double multiplier) {
        AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (instance == null) return;

        AttributeModifier existing = instance.getModifier(SPEED_ID);
        if (multiplier <= 1.0D) {
            if (existing != null) instance.removeModifier(SPEED_ID);
            return;
        }

        double amount = multiplier - 1.0D;
        if (existing != null && existing.amount() == amount) return;
        if (existing != null) instance.removeModifier(SPEED_ID);
        instance.addTransientModifier(
                new AttributeModifier(SPEED_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }
}
