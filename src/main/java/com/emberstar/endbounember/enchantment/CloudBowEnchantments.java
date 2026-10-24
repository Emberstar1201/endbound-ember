package com.emberstar.endbounember.enchantment;

import java.util.Optional;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.CloudArrowVelocityPayload;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 云来弓法 / 古云来弓法：只能附魔在弓与弩上。
 * 云来弓法把拉弓与上膛时间降低 75%；古云来弓法在此之上把箭矢速度提升到原版的 4.5 倍。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class CloudBowEnchantments {
    public static final ResourceKey<Enchantment> CLOUD_BOW = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "cloud_bow"));
    public static final ResourceKey<Enchantment> ANCIENT_CLOUD_BOW = ResourceKey.create(Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "ancient_cloud_bow"));

    /** 每 tick 额外减少的蓄力时间：总速度 ×4，也就是时间降低 75%。 */
    private static final int EXTRA_DRAW_TICKS = 3;
    /** 古云来弓法的箭矢速度倍率。 */
    private static final double ARROW_SPEED_MULTIPLIER = 4.5D;
    /** 原版生成实体包对速度分量的钳制上限（格/刻），超过该值的分量会在客户端被截断。 */
    private static final double VELOCITY_PACKET_LIMIT = 3.9D;

    private CloudBowEnchantments() {
    }

    public static int levelOf(ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) return 0;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Optional<ResourceKey<Enchantment>> entryKey = entry.getKey().unwrapKey();
            if (entryKey.isPresent() && entryKey.get().equals(key)) return entry.getIntValue();
        }
        return 0;
    }

    public static boolean hasCloudBow(ItemStack stack) {
        return levelOf(stack, CLOUD_BOW) > 0;
    }

    public static boolean hasAncientCloudBow(ItemStack stack) {
        return levelOf(stack, ANCIENT_CLOUD_BOW) > 0;
    }

    private static boolean hasAny(ItemStack stack) {
        return hasCloudBow(stack) || hasAncientCloudBow(stack);
    }

    /** 拉弓 / 上膛加速：每次使用 tick 额外扣掉 3 tick 的蓄力时间。 */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem();
        if (!hasAny(stack)) return;

        int fullCharge;
        if (stack.getItem() instanceof CrossbowItem) {
            fullCharge = CrossbowItem.getChargeDuration(stack, player);
        } else if (stack.getItem() instanceof BowItem) {
            fullCharge = 20;
        } else {
            return;
        }

        if (player.getTicksUsingItem() >= fullCharge) return;
        if (event.getDuration() <= EXTRA_DRAW_TICKS) return;
        event.setDuration(event.getDuration() - EXTRA_DRAW_TICKS);
    }

    /** 古云来弓法：刚射出的箭矢速度提升到原版的 4.5 倍。 */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (arrow.tickCount > 0) return;

        Entity owner = arrow.getOwner();
        if (!(owner instanceof Player player)) return;

        boolean ancient = hasAncientCloudBow(player.getMainHandItem())
                || hasAncientCloudBow(player.getOffhandItem());
        if (!ancient) return;

        arrow.setDeltaMovement(arrow.getDeltaMovement().scale(ARROW_SPEED_MULTIPLIER));
    }

    /**
     * 原版的实体生成包（ClientboundAddEntityPacket）会把速度分量钳制到 ±3.9 格/刻再发给客户端，
     * 高速箭矢会被客户端按普通速度模拟，渲染位置与箭尾粒子因此和服务端错位。
     * 该事件在生成包发出之后触发，此时客户端已经创建好实体，可以直接补发真实速度。
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof AbstractArrow arrow)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Vec3 velocity = arrow.getDeltaMovement();
        boolean clamped = Math.abs(velocity.x) > VELOCITY_PACKET_LIMIT
                || Math.abs(velocity.y) > VELOCITY_PACKET_LIMIT
                || Math.abs(velocity.z) > VELOCITY_PACKET_LIMIT;
        if (!clamped) return;

        PacketDistributor.sendToPlayer(player, new CloudArrowVelocityPayload(
                arrow.getId(), velocity.x, velocity.y, velocity.z));
    }
}
