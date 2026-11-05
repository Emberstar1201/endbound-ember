package com.emberstar.endbounember.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.emberstar.endbounember.world.TimeTurbulenceAccess;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.TeleportCommand;

/**
 * 被时间乱流许可拦下的传送指令，不再向执行者打印原版的 “Teleported” 成功提示。
 * <p>
 * 传送本身已经在 {@code ServerPlayer#teleportTo} 处被取消，原版提示却仍会照发，
 * 这里在传送指令发送成功消息时把它吞掉，避免和挂起申请的提示同时出现。
 */
@Mixin(TeleportCommand.class)
public abstract class TeleportCommandFeedbackMixin {
    @Redirect(
            method = {"teleportToPos", "teleportToEntity"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/commands/CommandSourceStack;sendSuccess(Ljava/util/function/Supplier;Z)V"))
    private static void endboundEmber$silenceBlockedTeleport(CommandSourceStack source,
            Supplier<Component> message, boolean allowLogging) {
        if (TimeTurbulenceAccess.consumeSilencedTeleportFeedback()) {
            return;
        }
        source.sendSuccess(message, allowLogging);
    }
}
