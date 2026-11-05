package com.emberstar.endbounember.client;

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** 一键打开本模组的配置界面，默认句点键，可在按键设置里自定义。 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class ConfigScreenKeyClient {
    public static final KeyMapping KEY = new KeyMapping("key.endbound_ember.open_config",
            GLFW.GLFW_KEY_PERIOD, "key.categories.endbound_ember");

    private ConfigScreenKeyClient() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) return;
        while (KEY.consumeClick()) {
            openConfig(minecraft);
        }
    }

    private static void openConfig(Minecraft minecraft) {
        ModContainer container = ModList.get().getModContainerById(EndboundEmber.MODID).orElse(null);
        if (container == null) return;
        container.getCustomExtension(IConfigScreenFactory.class)
                .ifPresent(factory -> minecraft.setScreen(factory.createScreen(container, minecraft.screen)));
    }
}
