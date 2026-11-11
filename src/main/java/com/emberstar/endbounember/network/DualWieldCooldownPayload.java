package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.DualWieldCooldownClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DualWieldCooldownPayload(int remainingTicks, int totalTicks) implements CustomPacketPayload {
    public static final Type<DualWieldCooldownPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "dual_wield_cooldown"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DualWieldCooldownPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DualWieldCooldownPayload::remainingTicks,
                    ByteBufCodecs.VAR_INT, DualWieldCooldownPayload::totalTicks,
                    DualWieldCooldownPayload::new);

    @Override
    public Type<DualWieldCooldownPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, DualWieldCooldownPayload::handle);
    }

    private static void handle(DualWieldCooldownPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (net.minecraft.client.Minecraft.getInstance().level != null) {
                DualWieldCooldownClient.update(packet.remainingTicks(), packet.totalTicks());
            } else {
                DualWieldCooldownClient.reset();
            }
        });
    }
}
