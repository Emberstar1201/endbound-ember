package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.enchantment.FlywheelEffectEnchantment;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端按下飞轮冲刺键时发送的空包。 */
public record FlywheelDashPayload() implements CustomPacketPayload {
    public static final Type<FlywheelDashPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "flywheel_dash"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FlywheelDashPayload> STREAM_CODEC =
            StreamCodec.unit(new FlywheelDashPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, FlywheelDashPayload::handle);
    }

    public static void handle(FlywheelDashPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) FlywheelEffectEnchantment.tryDash(player);
        });
    }
}
