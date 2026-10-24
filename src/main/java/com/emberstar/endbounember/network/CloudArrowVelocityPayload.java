package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.CloudArrowVelocityClient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：同步高速箭矢的真实速度。
 * 原版生成实体的数据包会把速度分量钳制到 ±3.9 格/刻，
 * 古云来弓法的箭矢会被客户端当成普通速度飞行，导致渲染与实际命中位置错位。
 */
public record CloudArrowVelocityPayload(int entityId, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<CloudArrowVelocityPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "cloud_arrow_velocity"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CloudArrowVelocityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CloudArrowVelocityPayload::entityId,
                    ByteBufCodecs.DOUBLE, CloudArrowVelocityPayload::x,
                    ByteBufCodecs.DOUBLE, CloudArrowVelocityPayload::y,
                    ByteBufCodecs.DOUBLE, CloudArrowVelocityPayload::z,
                    CloudArrowVelocityPayload::new);

    @Override
    public Type<CloudArrowVelocityPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, CloudArrowVelocityPayload::handle);
    }

    public static void handle(CloudArrowVelocityPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> CloudArrowVelocityClient.apply(packet.entityId, packet.x, packet.y, packet.z));
    }
}
