package com.emberstar.endbounember.network;

import com.emberstar.endbounember.entity.ZombieGirl;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record GirlInteractionPayload(int entityId, int action, String name) implements CustomPacketPayload {
    public static final Type<GirlInteractionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("endbound_ember", "girl_interaction"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GirlInteractionPayload> STREAM_CODEC = new StreamCodec<>() {
        public GirlInteractionPayload decode(RegistryFriendlyByteBuf b) { return new GirlInteractionPayload(b.readVarInt(), b.readVarInt(), b.readUtf(32)); }
        public void encode(RegistryFriendlyByteBuf b, GirlInteractionPayload p) { b.writeVarInt(p.entityId); b.writeVarInt(p.action); b.writeUtf(p.name == null ? "" : p.name, 32); }
    };
    public static final int FOLLOW = 1, STAY = 2, RENAME = 3, AFFECTION = 4;
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, GirlInteractionPayload::handle);
    }

    public static void handle(GirlInteractionPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getEntity(packet.entityId) instanceof ZombieGirl girl)
                    || !girl.isAlive() || girl.isRemoved() || player.distanceToSqr(girl) > 36.0D
                    || !girl.isTamed() || !girl.isOwnedBy(player)) return;
            switch (packet.action) {
                case FOLLOW -> girl.setOrderedToSit(false);
                case STAY -> girl.setOrderedToSit(true);
                case AFFECTION -> girl.changeAffection(1);
                case RENAME -> {
                    String value = packet.name == null ? "" : packet.name.trim();
                    if (!value.isEmpty() && value.length() <= 32) girl.setCustomName(Component.literal(value));
                }
                default -> { }
            }
        });
    }
}
