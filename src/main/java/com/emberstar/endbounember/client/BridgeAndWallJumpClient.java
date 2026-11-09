package com.emberstar.endbounember.client;

import org.lwjgl.glfw.GLFW;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.network.WallJumpPayload;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
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
        if (!Config.BEDROCK_BRIDGING_ENABLED.get() || bridgeAttemptCooldown > 0 || minecraft.player.onGround()
                || !minecraft.options.keyUse.isDown()
                || !(minecraft.player.getMainHandItem().getItem() instanceof BlockItem)) return;
        if (minecraft.hitResult != null && minecraft.hitResult.getType() != HitResult.Type.MISS) return;

        Vec3 look = minecraft.player.getLookAngle();
        BlockPos target = BlockPos.containing(minecraft.player.getX() - look.x * 0.65D,
                minecraft.player.getY() - 0.2D, minecraft.player.getZ() - look.z * 0.65D);
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            BlockPos support = target.relative(direction);
            if (!minecraft.level.getBlockState(support).blocksMotion()) continue;
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support), direction.getOpposite(), support, false);
            if (minecraft.player.blockInteractionRange() < minecraft.player.getEyePosition().distanceTo(hit.getLocation())) continue;
            minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND, hit);
            minecraft.player.swing(InteractionHand.MAIN_HAND);
            bridgeAttemptCooldown = 4;
            break;
        }
    }
}
