package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.TimeHaloSkills;
import com.emberstar.endbounember.world.TimeTurbulenceLevels;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端按下时光光环技能键时发送的包：0 时间变慢、1 时间暂停、2 时间倒流。 */
public record TimeHaloSkillPayload(int action) implements CustomPacketPayload {
    public static final int SLOW = 0;
    public static final int STOP = 1;
    public static final int REWIND = 2;

    public static final Type<TimeHaloSkillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "time_halo_skill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeHaloSkillPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, TimeHaloSkillPayload::action, TimeHaloSkillPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, TimeHaloSkillPayload::handle);
    }

    public static void handle(TimeHaloSkillPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && TimeTurbulenceLevels.wearsTimeHalo(player)) {
                TimeHaloSkills.handle(player, packet.action());
            }
        });
    }
}
