package com.emberstar.endbounember.enchantment;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.MovementSpeedState;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 深海涟漪提供水下移动与缓慢耗氧效果。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class DeepSeaRippleEnchantment {
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID,
            "deep_sea_ripple_speed");

    private DeepSeaRippleEnchantment() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return;

        boolean active = Config.DEEP_SEA_RIPPLE_ENABLED.get() && player.isInWater() && !player.isPassenger();
        // 只调整移动倍率，最低档仍保留原来的缓慢耗氧效果。
        manageSpeedModifier(serverPlayer, active ? MovementSpeedState.waterMultiplier(player) : 1.0D);
        if (active && player.getAirSupply() < player.getMaxAirSupply() && player.getAirSupply() > 0
                && player.tickCount % 10 != 0) {
            player.setAirSupply(player.getAirSupply() + 1);
        }
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
