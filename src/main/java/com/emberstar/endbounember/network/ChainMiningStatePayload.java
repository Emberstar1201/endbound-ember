package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.ChainMiningClientState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChainMiningStatePayload(boolean enabled, int mode) implements CustomPacketPayload {
    public static final Type<ChainMiningStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "chain_mining_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChainMiningStatePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ChainMiningStatePayload::enabled,
                    ByteBufCodecs.VAR_INT, ChainMiningStatePayload::mode, ChainMiningStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, ChainMiningStatePayload::handle);
    }

    private static void handle(ChainMiningStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ChainMiningClientState.set(payload.enabled(), payload.mode()));
    }
}
