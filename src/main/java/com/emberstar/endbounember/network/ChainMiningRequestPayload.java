package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.ChainMiningState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChainMiningRequestPayload(boolean cycleMode) implements CustomPacketPayload {
    public static final Type<ChainMiningRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "chain_mining_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ChainMiningRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ChainMiningRequestPayload::cycleMode,
                    ChainMiningRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, ChainMiningRequestPayload::handle);
    }

    private static void handle(ChainMiningRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ChainMiningState.State state = payload.cycleMode()
                    ? ChainMiningState.cycle(player)
                    : ChainMiningState.toggle(player);
            PacketDistributor.sendToPlayer(player, new ChainMiningStatePayload(state.enabled(), state.mode().ordinal()));
        });
    }
}
