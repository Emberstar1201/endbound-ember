package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.entity.HerobrineBossMusic;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：上报本地实际播放的 BOSS 曲目时长。
 * <p>自定义曲目只存在于客户端资源包，服务端读不到对应 OGG，只能按客户端时长安排循环，
 * 否则会按自带默认曲目的时长在音乐中途从头重播。</p>
 */
public record BossMusicLengthPayload(int ticks) implements CustomPacketPayload {
    public static final Type<BossMusicLengthPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "boss_music_length"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BossMusicLengthPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BossMusicLengthPayload::ticks,
                    BossMusicLengthPayload::new);

    @Override
    public Type<BossMusicLengthPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, BossMusicLengthPayload::handle);
    }

    public static void handle(BossMusicLengthPayload packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> HerobrineBossMusic.setClientLoopTicks(player, packet.ticks()));
        }
    }
}
