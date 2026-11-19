package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.EndboundEmberClient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 服务端只通知一次施法位置；客户端让同一圈火焰向外扩散。 */
public record ShockwaveVisualPayload(double x, double y, double z, boolean enhanced, double radius)
        implements CustomPacketPayload {
    public static final Type<ShockwaveVisualPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "shockwave_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShockwaveVisualPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, ShockwaveVisualPayload::x,
                    ByteBufCodecs.DOUBLE, ShockwaveVisualPayload::y,
                    ByteBufCodecs.DOUBLE, ShockwaveVisualPayload::z,
                    ByteBufCodecs.BOOL, ShockwaveVisualPayload::enhanced,
                    ByteBufCodecs.DOUBLE, ShockwaveVisualPayload::radius,
                    ShockwaveVisualPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, ShockwaveVisualPayload::handle);
    }

    private static void handle(ShockwaveVisualPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> EndboundEmberClient.spawnShockwaveRing(
                payload.x, payload.y, payload.z, payload.enhanced, payload.radius));
    }
}
