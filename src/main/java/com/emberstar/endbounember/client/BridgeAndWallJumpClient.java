package com.emberstar.endbounember.client;

/*
 * 基岩走搭部分移植并改编自 Bridging Mod 2.7.0（ver/1.21.1 分支）。
 * Bridging Mod: Copyright (c) 2023 Will Scully, MIT License.
 * 上游仓库: https://github.com/squeeglii/BridgingMod
 * 许可证全文与使用说明见项目根目录 THIRD-PARTY-NOTICES.md。
 */

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.WallJumpPayload;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class BridgeAndWallJumpClient {
    public static final KeyMapping WALL_JUMP_KEY = new KeyMapping(
            "key.endbound_ember.wall_jump", GLFW.GLFW_KEY_LEFT_SHIFT, "key.categories.endbound_ember");

    private static boolean wallJumpWasDown;
    private static int bridgeAttemptCooldown;
    private static int wallJumpHeartbeat;

    private BridgeAndWallJumpClient() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(WALL_JUMP_KEY);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            wallJumpWasDown = false;
            return;
        }
        if (minecraft.screen != null) {
            if (wallJumpWasDown) PacketDistributor.sendToServer(new WallJumpPayload(false));
            wallJumpWasDown = false;
            return;
        }

        boolean wallJumpDown = Config.WALL_JUMP_ENABLED.get() && WALL_JUMP_KEY.isDown();
        if (wallJumpDown && (!wallJumpWasDown || ++wallJumpHeartbeat >= 5)) {
            PacketDistributor.sendToServer(new WallJumpPayload(true));
            wallJumpHeartbeat = 0;
        } else if (!wallJumpDown && wallJumpWasDown) {
            PacketDistributor.sendToServer(new WallJumpPayload(false));
            wallJumpHeartbeat = 0;
        }
        wallJumpWasDown = wallJumpDown;

        if (bridgeAttemptCooldown > 0) bridgeAttemptCooldown--;
        if (!Config.BEDROCK_BRIDGING_ENABLED.get() || bridgeAttemptCooldown > 0
                || !minecraft.options.keyUse.isDown()
                || !(minecraft.player.getMainHandItem().getItem() instanceof BlockItem)) return;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) return;

        BridgeTarget target = findBridgeTarget(minecraft);
        if (target == null) return;

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target.position()), target.face(), target.position(), true);
        minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
        minecraft.player.swing(InteractionHand.MAIN_HAND);
        bridgeAttemptCooldown = 4;
    }

    private static BridgeTarget findBridgeTarget(Minecraft minecraft) {
        Vec3 camera = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle();
        double reach = minecraft.player.blockInteractionRange();
        Vec3 end = minecraft.player.getEyePosition().add(look.scale(reach));

        for (BlockPos position : calculateVoxelPath(BlockPos.containing(camera), BlockPos.containing(end))) {
            BlockState state = minecraft.level.getBlockState(position);
            if (!state.isAir()) {
                continue;
            }
            if (minecraft.player.getBoundingBox().intersects(Vec3.atLowerCornerOf(position),
                    Vec3.atLowerCornerOf(position).add(1.0D, 1.0D, 1.0D))) {
                continue;
            }

            for (Direction side : getValidFaces(look)) {
                BlockPos support = position.relative(side);
                BlockState supportState = minecraft.level.getBlockState(support);
                if (minecraft.level.isEmptyBlock(support) || supportState.getBlock() instanceof LiquidBlock
                        || supportState.canBeReplaced()) {
                    continue;
                }
                Direction hitFace = side.getOpposite();
                if (!minecraft.player.mayUseItemAt(position, hitFace, minecraft.player.getMainHandItem())) {
                    continue;
                }
                return new BridgeTarget(position, hitFace);
            }
        }
        return null;
    }

    private static List<Direction> getValidFaces(Vec3 look) {
        List<Direction> faces = new LinkedList<>();
        for (Direction direction : Direction.values()) {
            double similarity = look.dot(Vec3.atLowerCornerOf(direction.getNormal()));
            if (similarity >= 0.1D) {
                faces.add(direction.getOpposite());
            }
        }
        return faces;
    }

    private static List<BlockPos> calculateVoxelPath(BlockPos start, BlockPos end) {
        List<BlockPos> points = new ArrayList<>();
        points.add(start);
        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();
        int dx = Math.abs(end.getX() - x);
        int dy = Math.abs(end.getY() - y);
        int dz = Math.abs(end.getZ() - z);
        int sx = Integer.compare(end.getX(), x);
        int sy = Integer.compare(end.getY(), y);
        int sz = Integer.compare(end.getZ(), z);
        int ax = 2 * dx;
        int ay = 2 * dy;
        int az = 2 * dz;

        if (dx >= dy && dx >= dz) {
            int yd = ay - dx;
            int zd = az - dx;
            while (x != end.getX()) {
                x += sx;
                if (yd >= 0) { y += sy; yd -= ax; }
                if (zd >= 0) { z += sz; zd -= ax; }
                yd += ay;
                zd += az;
                points.add(new BlockPos(x, y, z));
            }
        } else if (dy >= dx && dy >= dz) {
            int xd = ax - dy;
            int zd = az - dy;
            while (y != end.getY()) {
                y += sy;
                if (xd >= 0) { x += sx; xd -= ay; }
                if (zd >= 0) { z += sz; zd -= ay; }
                xd += ax;
                zd += az;
                points.add(new BlockPos(x, y, z));
            }
        } else {
            int yd = ay - dz;
            int xd = ax - dz;
            while (z != end.getZ()) {
                z += sz;
                if (yd >= 0) { y += sy; yd -= az; }
                if (xd >= 0) { x += sx; xd -= az; }
                yd += ay;
                xd += ax;
                points.add(new BlockPos(x, y, z));
            }
        }
        return points;
    }

    private record BridgeTarget(BlockPos position, Direction face) {
    }
}
