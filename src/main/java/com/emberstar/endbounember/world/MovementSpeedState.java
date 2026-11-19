package com.emberstar.endbounember.world;

import java.util.Locale;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** 两种移动能力的独立调速档位，仅由服务端修改并持久化。 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class MovementSpeedState {
    public static final int DEEP_SEA = 0;
    public static final int SOARING = 1;
    private static final int DEFAULT_STEP = 16;
    private static final String WATER_KEY = EndboundEmber.MODID + ":deep_sea_speed_step";
    private static final String SOARING_KEY = EndboundEmber.MODID + ":soaring_speed_step";

    private MovementSpeedState() {
    }

    /** 滑翔优先，防止同时处于水中时一次输入作用到错误的能力。 */
    public static int activeSkill(Player player) {
        if (player.isFallFlying()) return Config.SMOKELESS_DASH_ENABLED.get() ? SOARING : -1;
        if (player.isInWater() && !player.isPassenger() && Config.DEEP_SEA_RIPPLE_ENABLED.get()) return DEEP_SEA;
        return -1;
    }

    public static double waterMultiplier(Player player) {
        return readStep(player, DEEP_SEA) / 10.0D;
    }

    public static double soaringHorizontalLimit(Player player) {
        return readStep(player, SOARING) / 10.0D;
    }

    private static String key(int skill) {
        return skill == DEEP_SEA ? WATER_KEY : SOARING_KEY;
    }

    private static int clampStep(int skill, int step) {
        return Math.max(skill == DEEP_SEA ? 10 : 2, Math.min(DEFAULT_STEP, step));
    }

    private static int readStep(Player player, int skill) {
        String key = key(skill);
        // 缺失或类型错误的数据采用原始速度；已有整数读取时也必须限制范围。
        return player.getPersistentData().contains(key, Tag.TAG_INT)
                ? clampStep(skill, player.getPersistentData().getInt(key)) : DEFAULT_STEP;
    }

    public static void adjust(ServerPlayer player, int skill, boolean increase) {
        // 不接受客户端给定速度，重新校验配置与运动状态，每包最多增减一档。
        if ((skill != DEEP_SEA && skill != SOARING) || activeSkill(player) != skill) return;
        int step = clampStep(skill, readStep(player, skill) + (increase ? 1 : -1));
        player.getPersistentData().putInt(key(skill), step);
        String feedback = skill == DEEP_SEA ? "message.endbound_ember.deep_sea_speed"
                : "message.endbound_ember.soaring_speed";
        player.displayClientMessage(Component.translatable(feedback,
                String.format(Locale.ROOT, "%.1f", step / 10.0D)), true);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // 玩家实体重建时分别继承两个档位，兼容死亡重生和返回主世界。
        for (int skill : new int[] { DEEP_SEA, SOARING }) {
            player.getPersistentData().putInt(key(skill), readStep(event.getOriginal(), skill));
        }
    }
}
