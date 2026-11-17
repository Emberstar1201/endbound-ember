package com.emberstar.endbounember.world;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class WindRippleToggle {
    private static final Map<UUID, Boolean> STATES = new ConcurrentHashMap<>();

    private WindRippleToggle() {
    }

    public static boolean isEnabled(Player player) {
        return STATES.getOrDefault(player.getUUID(), false);
    }

    public static boolean toggle(ServerPlayer player) {
        boolean enabled = !isEnabled(player);
        STATES.put(player.getUUID(), enabled);
        return enabled;
    }

    public static void clear(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }
}
