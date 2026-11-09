package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.client.SwordBlockingHud;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：完美格挡成功，通知 HUD 把格挡条回满。
 */
public record SwordParryPayload() implements CustomPacketPayload {
    public static final Type<SwordParryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "sword_parry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwordParryPayload> STREAM_CODEC =
            StreamCodec.unit(new SwordParryPayload());

    @Override
    public Type<SwordParryPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, SwordParryPayload::handle);
    }

    public static void handle(SwordParryPayload packet, IPayloadContext context) {
        context.enqueueWork(SwordBlockingHud::onPerfectParry);
    }
}
