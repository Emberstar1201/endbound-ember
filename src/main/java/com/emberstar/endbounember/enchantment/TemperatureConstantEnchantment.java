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
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 温度恒定：护甲任意部位，最高 I 级。
 * 任意一件护甲带有本附魔即全局生效：免疫冰冻（细雪）与高温（火焰/岩浆/灼热地面）伤害，
 * 并持续清除冻结计时。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class TemperatureConstantEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "temperature_constant"));

    /** 对应旧版配置 temperatureConstant.enabled（默认 true）。 */
    private static final boolean ENABLED = true;
    /** 对应旧版配置 temperatureConstant.preventFreeze（默认 true）。 */
    private static final boolean PREVENT_FREEZE = true;
    /** 对应旧版配置 temperatureConstant.preventHeat（默认 true）。 */
    private static final boolean PREVENT_HEAT = true;
    /** 对应旧版 TemperatureConstantHandler 中脱困后削减的火焰计时。 */
    private static final int FIRE_TICK_REDUCTION = 40;

    private static final String LAST_HEAT_SOURCE_KEY = "temperature_constant_last_heat_source";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private TemperatureConstantEnchantment() {
    }

    /** 读取物品上的温度恒定等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    private static boolean hasEnchant(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (levelOf(player.getItemBySlot(slot)) > 0) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!ENABLED) return;
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!hasEnchant(player)) return;

        DamageSource source = event.getSource();
        if (PREVENT_FREEZE && source.is(DamageTypeTags.IS_FREEZING)) {
            event.setCanceled(true);
            player.setTicksFrozen(0);
            return;
        }
        if (PREVENT_HEAT && source.is(DamageTypeTags.IS_FIRE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!ENABLED || !hasEnchant(player)) return;

        if (PREVENT_FREEZE && player.getTicksFrozen() > 0) {
            player.setTicksFrozen(0);
        }

        boolean heatSource = player.isInLava()
                || player.level().getBlockState(player.blockPosition()).is(BlockTags.FIRE)
                || player.level().getBlockState(player.blockPosition().below()).is(BlockTags.FIRE);
        boolean wasInHeatSource = player.getPersistentData().getBoolean(LAST_HEAT_SOURCE_KEY);
        if (wasInHeatSource && !heatSource && player.getRemainingFireTicks() > 0) {
            player.setRemainingFireTicks(Math.max(0, player.getRemainingFireTicks() - FIRE_TICK_REDUCTION));
        }
        player.getPersistentData().putBoolean(LAST_HEAT_SOURCE_KEY, heatSource);
    }
}
