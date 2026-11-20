package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.WindRippleToggle;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端按键后把目标开关状态同步给服务端，不再让服务端自行取反。 */
public record WindRipplePayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<WindRipplePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "wind_ripple"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WindRipplePayload> STREAM_CODEC =
            StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.BOOL, WindRipplePayload::enabled,
                    WindRipplePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, WindRipplePayload::handle);
    }

    private static void handle(WindRipplePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                WindRippleToggle.set(player, payload.enabled());
            }
        });
    }
}
