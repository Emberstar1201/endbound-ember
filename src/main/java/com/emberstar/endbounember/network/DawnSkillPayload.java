package com.emberstar.endbounember.network;

import com.emberstar.endbounember.enchantment.DawnEnchantment;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端按下拂晓技能键时发送的空包。 */
public record DawnSkillPayload() implements CustomPacketPayload {
    public static final Type<DawnSkillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("endbound_ember", "dawn_skill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DawnSkillPayload> STREAM_CODEC =
            StreamCodec.unit(new DawnSkillPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, DawnSkillPayload::handle);
    }

    public static void handle(DawnSkillPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) DawnEnchantment.tryCast(player);
        });
    }
}
