package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.advancements.DisplayInfo;

/**
 * 临时的成就公告开关。
 *
 * <p>「不被世界承认的少女」在玩家未佩戴星尘光环时需要连聊天栏公告也一并压掉，
 * 否则解锁瞬间聊天栏就会把真实成就名写出来。</p>
 */
@Mixin(DisplayInfo.class)
public interface DisplayInfoAccessor {

    @Accessor("announceChat")
    boolean endbound$getAnnounceChat();

    @Mutable
    @Accessor("announceChat")
    void endbound$setAnnounceChat(boolean announceChat);
}
