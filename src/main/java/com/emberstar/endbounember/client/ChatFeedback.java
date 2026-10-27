package com.emberstar.endbounember.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 聊天栏反馈。
 * 原版聊天栏在界面打开时仍会接收消息，所以请求即使跨界面返回也不会丢失；
 * 额外在动作栏提示一次，避免玩家关掉界面后只盯着画面中央而错过回复。
 */
final class ChatFeedback {
    private ChatFeedback() {
    }

    static void reply(String girlName, String response) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.getChat().addMessage(Component.literal("<" + girlName + "> " + response));
        minecraft.gui.setOverlayMessage(
                Component.translatable("gui.endbound_ember.ai.replied", girlName), false);
    }

    static void waiting(String girlName) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setOverlayMessage(
                Component.translatable("gui.endbound_ember.ai.waiting_hint", girlName), false);
    }

    static void error(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.getChat().addMessage(message.copy().withStyle(ChatFormatting.RED));
        minecraft.gui.setOverlayMessage(message, false);
    }
}
