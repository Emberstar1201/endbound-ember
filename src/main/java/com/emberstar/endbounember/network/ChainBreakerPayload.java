package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.enchantment.ChainBreakerEnchantment;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端连锁挖掘键状态变化时发送的包。 */
public record ChainBreakerPayload(boolean active) implements CustomPacketPayload {
    public static final Type<ChainBreakerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "chain_breaker_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChainBreakerPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ChainBreakerPayload::active, ChainBreakerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, ChainBreakerPayload::handle);
    }

    public static void handle(ChainBreakerPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ChainBreakerEnchantment.setChainMode(player.getUUID(), packet.active());
                player.displayClientMessage(Component.translatable(packet.active()
                        ? "endbound_ember.chain_breaker.mode_on"
                        : "endbound_ember.chain_breaker.mode_off"), true);
            }
        });
    }
}
