package com.emberstar.endbounember.world;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

public final class ChainMiningState {
    public enum Mode {
        VEIN, TUNNEL, STAIRS_UP, STAIRS_DOWN;

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    public record State(boolean enabled, Mode mode) {
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private ChainMiningState() {
    }

    public static State get(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), ignored -> new State(false, Mode.VEIN));
    }

    public static State toggle(ServerPlayer player) {
        State old = get(player);
        State next = new State(!old.enabled(), old.mode());
        STATES.put(player.getUUID(), next);
        return next;
    }

    public static State cycle(ServerPlayer player) {
        State old = get(player);
        State next = new State(old.enabled(), old.mode().next());
        STATES.put(player.getUUID(), next);
        return next;
    }

    public static void remove(UUID playerId) {
        STATES.remove(playerId);
    }
}
