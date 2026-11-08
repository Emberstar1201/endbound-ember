package com.emberstar.endbounember.enchantment;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 画饼充饥：武器（剑/斧/三叉戟/弓/弩）专属，最高 III 级。
 * 命中生物时按等级恢复饥饿值与饱食度，冷却 10 tick。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class IllusoryFeastEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "illusory_feast"));

    /** 对应旧版配置 illusoryFeast.level1Food（默认 1），索引 0 占位。 */
    private static final int[] FOOD_AMOUNTS = { 0, 1, 2, 3 };
    /** 对应旧版配置 illusoryFeast.level1Saturation（默认 0.5）等，索引 0 占位。 */
    private static final float[] SATURATION_MODIFIERS = { 0.0F, 0.5F, 1.0F, 1.5F };
    /** 对应旧版 IllusoryFeastHandler 中的固定冷却（10 tick）。 */
    private static final int COOLDOWN_TICKS = 10;

    private static final Map<UUID, Long> LAST_RESTORE = new ConcurrentHashMap<>();

    private IllusoryFeastEnchantment() {
    }

    /** 读取物品上的画饼充饥等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        int level = levelOf(attacker.getMainHandItem());
        if (level <= 0) return;

        long gameTime = attacker.level().getGameTime();
        long last = LAST_RESTORE.getOrDefault(attacker.getUUID(), Long.MIN_VALUE);
        if (gameTime - last < COOLDOWN_TICKS) return;
        LAST_RESTORE.put(attacker.getUUID(), gameTime);

        int index = Math.min(level, 3);
        attacker.getFoodData().eat(FOOD_AMOUNTS[index], SATURATION_MODIFIERS[index]);
    }
}
