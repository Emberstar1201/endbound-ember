package com.emberstar.endbounember.enchantment;

import java.util.List;
import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 嗜血：武器附魔，最高 I 级。
 * 攻击命中时回复自身 10% 最大生命值，冷却 100 tick（5 秒）。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class BloodthirstEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "bloodthirst"));

    /** 旧版 Config.bloodthirst.healPercent 默认值：10%。 */
    private static final double HEAL_PERCENT = 10.0D;
    /** 旧版 Config.bloodthirst.cooldownTicks 默认值：100 tick。 */
    private static final int COOLDOWN_TICKS = 100;

    /** 旧版 BloodthirstHandler.TAG_ROOT。 */
    private static final String TAG_ROOT = "EndboundEmberBloodthirst";
    /** 旧版 BloodthirstHandler.KEY_LAST_TRIGGER。 */
    private static final String KEY_LAST_TRIGGER = "lastTriggerTime";

    private static final int TOOLTIP_COLOR = 0xB03A2E;

    private BloodthirstEnchantment() {
    }

    /** 读取物品上的嗜血附魔等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    /** 近战命中：冷却结束后回复自身最大生命值的 10%。 */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (event.getSource().getDirectEntity() != player) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        if (levelOf(player.getMainHandItem()) <= 0) return;

        CompoundTag data = player.getPersistentData();
        CompoundTag cooldown = data.getCompound(TAG_ROOT);
        long gameTime = player.level().getGameTime();
        long lastTrigger = cooldown.getLong(KEY_LAST_TRIGGER);
        if (gameTime - lastTrigger < COOLDOWN_TICKS) return;

        float healAmount = player.getMaxHealth() * (float) (HEAL_PERCENT / 100.0D);
        player.heal(healAmount);

        cooldown.putLong(KEY_LAST_TRIGGER, gameTime);
        data.put(TAG_ROOT, cooldown);
    }

    /** 物品提示：在附魔名称之后追加效果说明。 */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (levelOf(stack) <= 0) return;

        List<Component> tooltip = event.getToolTip();
        String enchantName = Component.translatable("enchantment.endbound_ember.bloodthirst").getString();
        int insertAt = tooltip.size();
        for (int i = 0; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().startsWith(enchantName)) {
                insertAt = i + 1;
                break;
            }
        }
        tooltip.add(insertAt, Component.translatable("endbound_ember.bloodthirst.desc")
                .withStyle(style -> style.withColor(TOOLTIP_COLOR)));
    }
}
