package com.emberstar.endbounember.network;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.TimeTurbulenceLevels;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 时光光环传送轮盘选择后的指令包：direction 取值：
 * 0 = 主世界，1 = 下界，2 = 末地，3 = 时间乱流（切换进出）。
 */
public record TimeTurbulenceTogglePayload(int direction) implements CustomPacketPayload {
    public static final int OVERWORLD = 0;
    public static final int NETHER = 1;
    public static final int END = 2;
    public static final int TIME_TURBULENCE = 3;

    public static final Type<TimeTurbulenceTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "time_turbulence_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TimeTurbulenceTogglePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, TimeTurbulenceTogglePayload::direction,
                    TimeTurbulenceTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, TimeTurbulenceTogglePayload::handle);
    }

    public static void handle(TimeTurbulenceTogglePayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && (TimeTurbulenceLevels.wearsTimeHalo(player)
                            || TimeTurbulenceLevels.isTimeTurbulence(player.level()))) {
                TimeTurbulenceLevels.goTo(player, packet.direction());
            }
        });
    }
}
