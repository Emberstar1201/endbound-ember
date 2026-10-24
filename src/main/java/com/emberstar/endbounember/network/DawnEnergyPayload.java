package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.DawnEnergyClient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：同步拂晓能量与成长等级，用于 HUD 能量条。
 */
public record DawnEnergyPayload(int energy, int level) implements CustomPacketPayload {
    public static final Type<DawnEnergyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "dawn_energy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DawnEnergyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, DawnEnergyPayload::energy,
                    ByteBufCodecs.VAR_INT, DawnEnergyPayload::level,
                    DawnEnergyPayload::new);

    @Override
    public Type<DawnEnergyPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, DawnEnergyPayload::handle);
    }

    public static void handle(DawnEnergyPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> DawnEnergyClient.update(packet.energy, packet.level));
    }
}
