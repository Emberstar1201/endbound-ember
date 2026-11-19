package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.MovementSpeedState;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端只提交能力类型和方向，不提交档位或速度。 */
public record MovementSpeedAdjustPayload(int skill, boolean increase) implements CustomPacketPayload {
    public static final Type<MovementSpeedAdjustPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "movement_speed_adjust"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovementSpeedAdjustPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, MovementSpeedAdjustPayload::skill,
                    ByteBufCodecs.BOOL, MovementSpeedAdjustPayload::increase, MovementSpeedAdjustPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, MovementSpeedAdjustPayload::handle);
    }

    private static void handle(MovementSpeedAdjustPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MovementSpeedState.adjust(player, payload.skill(), payload.increase());
            }
        });
    }
}
