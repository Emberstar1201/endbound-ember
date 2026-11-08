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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 幽匿行者：靴子专属，最高 I 级。
 * 旧版通过 mixin 让监守者无法侦测玩家，并屏蔽玩家产生的振动。
 * 此处在不使用 mixin 的前提下，改用 NeoForge 事件做语义最接近的实现：
 * 周期性清除附近监守者对玩家的仇恨并解除其锁定，同时使其攻击无法伤害玩家。
 * 震荡（幽匿感测体/尖啸体）无法通过事件拦截，因此仍可能被触发。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class DarkWalkerEnchantment {
    public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "dark_walker"));

    /** 对应旧版监守者实体感知器的侦测半径（24 格）。 */
    private static final double WARDEN_RADIUS = 24.0D;
    /** 清除仇恨的周期（tick），用于替代旧版 mixin 的持续屏蔽。 */
    private static final int ANGER_CLEAR_INTERVAL = 10;

    private DarkWalkerEnchantment() {
    }

    /** 读取物品上的幽匿行者等级，附魔书存放在 STORED_ENCHANTMENTS 中。 */
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

    private static boolean isActive(Player player) {
        return levelOf(player.getItemBySlot(EquipmentSlot.FEET)) > 0;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;
        if (!isActive(player)) return;
        if (player.tickCount % ANGER_CLEAR_INTERVAL != 0) return;

        AABB area = player.getBoundingBox().inflate(WARDEN_RADIUS);
        for (Warden warden : player.level().getEntitiesOfClass(Warden.class, area)) {
            warden.clearAnger(player);
            if (warden.getTarget() == player) {
                warden.setTarget(null);
            }
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isActive(player)) return;
        if (!(event.getSource().getEntity() instanceof Warden)
                && !(event.getSource().getDirectEntity() instanceof Warden)) {
            return;
        }
        event.setCanceled(true);
    }
}
