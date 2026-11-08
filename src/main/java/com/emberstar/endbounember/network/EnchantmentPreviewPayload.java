package com.emberstar.endbounember.network;

import java.util.ArrayList;
import java.util.List;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端算好的附魔台三档结果，用来在界面上直接显示“这一档会附出什么”。
 * <p>只包含结果本身，不参与任何附魔逻辑；客户端仅做展示。</p>
 */
public record EnchantmentPreviewPayload(int containerId, List<List<Entry>> rows) implements CustomPacketPayload {
    /** 单个附魔及其等级 */
    public record Entry(ResourceLocation enchantment, int level) {
    }

    public static final Type<EnchantmentPreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "enchant_preview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantmentPreviewPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.containerId);
                for (int row = 0; row < 3; row++) {
                    List<Entry> entries = payload.rows.get(row);
                    buffer.writeByte(entries.size());
                    for (Entry entry : entries) {
                        buffer.writeResourceLocation(entry.enchantment);
                        buffer.writeVarInt(entry.level);
                    }
                }
            },
            buffer -> {
                int containerId = buffer.readVarInt();
                List<List<Entry>> rows = new ArrayList<>(3);
                for (int row = 0; row < 3; row++) {
                    int size = buffer.readUnsignedByte();
                    List<Entry> entries = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        entries.add(new Entry(buffer.readResourceLocation(), buffer.readVarInt()));
                    }
                    rows.add(List.copyOf(entries));
                }
                return new EnchantmentPreviewPayload(containerId, List.copyOf(rows));
            });

    /** 三档都没有结果时使用的空数据 */
    public static EnchantmentPreviewPayload empty(int containerId) {
        return new EnchantmentPreviewPayload(containerId, List.of(List.of(), List.of(), List.of()));
    }

    public static void send(net.minecraft.server.level.ServerPlayer player, EnchantmentPreviewPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TYPE, STREAM_CODEC, EnchantmentPreviewPayload::handle);
    }

    public static void handle(EnchantmentPreviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> EnchantmentPreviewClientCache.accept(payload));
    }
}
