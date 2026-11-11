package com.emberstar.endbounember.network;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.world.DualWieldCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DualWieldAttackPayload() implements CustomPacketPayload {
    public static final Type<DualWieldAttackPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("endbound_ember", "dual_wield_attack"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DualWieldAttackPayload> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {}, buffer -> new DualWieldAttackPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, DualWieldAttackPayload::handle);
    }

    private static void handle(DualWieldAttackPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Config.DUAL_WIELD_ENABLED.get() && context.player() instanceof ServerPlayer player) {
                DualWieldCombat.attackOffhand(player);
            }
        });
    }
}
