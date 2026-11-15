package com.emberstar.endbounember.client;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.DualWieldAttackPayload;
import com.emberstar.endbounember.world.DualWieldCombat;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class DualWieldClient {
    private static final KeyMapping OFFHAND_ATTACK = new KeyMapping(
            "key.endbound_ember.dual_wield_attack",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.endbound_ember");

    private DualWieldClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OFFHAND_ATTACK);
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft minecraft = Minecraft.getInstance();
        // #region debug-point 双持拦截右键
        boolean dualEnabled = Config.DUAL_WIELD_ENABLED.get();
        boolean matching = minecraft.player != null && DualWieldCombat.hasMatchingDualWeapon(minecraft.player);
        EndboundEmber.LOGGER.info("[双持诊断] 右键使用 hand={} 双持开关={} 双持匹配={} 主手={} 副手={}",
                event.getHand(), dualEnabled, matching,
                minecraft.player == null ? "无"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(minecraft.player.getMainHandItem().getItem()),
                minecraft.player == null ? "无"
                        : net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(minecraft.player.getOffhandItem().getItem()));
        // #endregion debug-point
        if (minecraft.player == null || !Config.DUAL_WIELD_ENABLED.get()
                || !DualWieldCombat.hasMatchingDualWeapon(minecraft.player)) return;
        event.setSwingHand(false);
        event.setCanceled(true);
        PacketDistributor.sendToServer(new DualWieldAttackPayload());
    }

    @SubscribeEvent
    public static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (OFFHAND_ATTACK.consumeClick()) {
            if (minecraft.player != null && Config.DUAL_WIELD_ENABLED.get()
                    && DualWieldCombat.hasMatchingDualWeapon(minecraft.player)) {
                PacketDistributor.sendToServer(new DualWieldAttackPayload());
            }
        }
    }
}
