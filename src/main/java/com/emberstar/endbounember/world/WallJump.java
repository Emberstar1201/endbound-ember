package com.emberstar.endbounember.world;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class WallJump {
    private static final double WALL_DISTANCE = 0.16D;
    private static final double UPWARD_SPEED = 0.62D;
    private static final double OUTWARD_SPEED = 0.48D;
    private static final int REPEAT_DELAY_TICKS = 8;
    private static final Map<UUID, Boolean> PRESSED = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COOLDOWN = new ConcurrentHashMap<>();

    private WallJump() {
    }

    public static void setPressed(ServerPlayer player, boolean pressed) {
        if (pressed) PRESSED.put(player.getUUID(), true);
        else PRESSED.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        int cooldown = COOLDOWN.getOrDefault(id, 0);
        if (cooldown > 0) COOLDOWN.put(id, cooldown - 1);

        if (!Config.WALL_JUMP_ENABLED.get() || !PRESSED.getOrDefault(id, false)
                || player.onGround() || player.getAbilities().flying || player.isFallFlying()
                || player.isInWater() || player.onClimbable() || player.isPassenger()
                || cooldown > 0) return;

        Direction wall = touchingWall(player);
        if (wall == null) return;

        Vec3 motion = player.getDeltaMovement();
        Vec3 outward = Vec3.atLowerCornerOf(wall.getNormal());
        player.setDeltaMovement(motion.x * 0.25D + outward.x * OUTWARD_SPEED,
                Math.max(motion.y, UPWARD_SPEED), motion.z * 0.25D + outward.z * OUTWARD_SPEED);
        player.fallDistance = 0.0F;
        player.hurtMarked = true;
        COOLDOWN.put(id, REPEAT_DELAY_TICKS);
    }

    private static Direction touchingWall(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(WALL_DISTANCE, 0.0D, WALL_DISTANCE);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos check = BlockPos.containing(player.getX() + direction.getStepX() * 0.55D,
                    player.getY() + player.getBbHeight() * 0.5D,
                    player.getZ() + direction.getStepZ() * 0.55D);
            if (player.level().getBlockState(check).blocksMotion()
                    && box.intersects(AABB.unitCubeFromLowerCorner(Vec3.atLowerCornerOf(check)))) return direction;
        }
        return null;
    }
}
