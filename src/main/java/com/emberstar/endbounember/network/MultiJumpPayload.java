package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.MultiJumpClient;
import com.emberstar.endbounember.world.MultiJump;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 多段跳请求与确认。
 *
 * @param sequence  客户端递增的请求序号，用于丢弃过期的服务端确认
 * @param remaining 剩余可跳次数；请求方向为客户端预测值，确认方向为服务端权威值
 * @param accepted  仅确认方向有效：服务端是否真正执行了这一次跳跃
 */
public record MultiJumpPayload(int sequence, int remaining, boolean accepted) implements CustomPacketPayload {
    public static final Type<MultiJumpPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "multi_jump"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MultiJumpPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MultiJumpPayload::sequence,
                    ByteBufCodecs.VAR_INT, MultiJumpPayload::remaining,
                    ByteBufCodecs.BOOL, MultiJumpPayload::accepted,
                    MultiJumpPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playBidirectional(TYPE, STREAM_CODEC, (payload, context) -> {
            if (context.flow().isServerbound()) {
                handleRequest(payload, context);
            } else {
                handleSync(payload, context);
            }
        });
    }

    private static void handleRequest(MultiJumpPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MultiJump.Result result = MultiJump.handleRequest(player, payload.sequence());
                // 服务端权威结果回传，客户端据此校正预测次数
                PacketDistributor.sendToPlayer(player,
                        new MultiJumpPayload(result.sequence(), result.remaining(), result.accepted()));
            }
        });
    }

    private static void handleSync(MultiJumpPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MultiJumpClient.applyServerResult(
                payload.sequence(), payload.remaining(), payload.accepted()));
    }
}
