package com.emberstar.endbounember.client;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.world.TimeTurbulenceLevels;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 时间乱流的时钟音效：秒针/分针走动声与整点钟声。
 * <p>
 * 只在客户端播放本机玩家的声音，因此每个玩家听到的都是属于自己维度里的钟声。
 * 时钟时间源与表盘、指针保持一致（{@link TimeHaloLayers#secondsInDay}）。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class TimeTurbulenceClient {
    /**
     * time_tick.ogg 是约 15 秒的整段时钟走动音频，本身已含连续多声“滴答”。
     * 因此按整段长度循环播放；若改成每秒触发，会同时叠放出多份音频，听起来像连续乱响。
     */
    private static final int TICK_SOUND_INTERVAL = 20 * 15;

    private static int lastHour = -1;
    /** 走动声剩余冷却刻；负值表示刚进入乱流，需要立刻起播 */
    private static int tickCooldown = -1;

    private TimeTurbulenceClient() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null || !TimeTurbulenceLevels.isTimeTurbulence(level)) {
            // 离开维度后重置，下次进入重新起播
            lastHour = -1;
            tickCooldown = -1;
            return;
        }

        int secondsInDay = (int) TimeHaloLayers.secondsInDay(player);
        int hour = secondsInDay / 3600;

        if (lastHour < 0) {
            lastHour = hour;
        }

        // 整段音频循环播放：一份播完再播下一份，避免多份走动声叠在一起
        if (tickCooldown <= 0) {
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    EndboundEmber.TIME_TICK_SOUND.get(), SoundSource.AMBIENT, 0.7F, 1.0F, false);
            tickCooldown = TICK_SOUND_INTERVAL;
        } else {
            tickCooldown--;
        }

        if (hour != lastHour) {
            lastHour = hour;
            level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                    EndboundEmber.TIME_CHIME_SOUND.get(), SoundSource.AMBIENT, 1.0F, 1.0F, false);
        }
    }
}
