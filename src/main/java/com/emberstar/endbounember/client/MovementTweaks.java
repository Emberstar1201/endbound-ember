package com.emberstar.endbounember.client;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 玩家移动机制：惯性滑行、落地连跳、空中控制增强。
 *
 * <p>移动本身由客户端计算，这里在玩家每 tick 结束时改写速度，不改变原版的运动公式，
 * 因此不会与服务端位置校验冲突。全部行为由 {@link Config} 中的开关控制。</p>
 */
@EventBusSubscriber(modid = EndboundEmber.MODID, value = Dist.CLIENT)
public final class MovementTweaks {
    /** 滑行时保留的水平速度比例。 */
    private static final double GLIDE_KEEP = 0.92D;
    /** 滑行每 tick 的衰减系数，越小停得越快。 */
    private static final double GLIDE_DECAY = 0.97D;
    /** 低于该速度视为停止滑行。 */
    private static final double GLIDE_MIN_SPEED = 0.05D;
    /** 空中从静止起步时的额外加速度。 */
    private static final double AIR_ACCEL = 0.04D;
    /** 空中改变方向时每 tick 的速度转向比例。 */
    private static final double AIR_TURN_RATE = 0.15D;
    /** 空中速度低于该值时直接朝输入方向加速。 */
    private static final double AIR_MIN_SPEED = 0.04D;
    /** 原版起跳初速度。 */
    private static final double JUMP_POWER = 0.42D;
    /** 距地面多少格以内允许触发落地连跳。 */
    private static final double HOP_GAP = 0.25D;

    /** 滑行保留的水平速度。 */
    private static double glideX;
    private static double glideZ;
    /** 本次滞空是否已经触发过连跳。 */
    private static boolean hopped;

    private MovementTweaks() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;

        if (!Config.MOVEMENT_TWEAKS_ENABLED.get()) {
            glideX = 0.0D;
            glideZ = 0.0D;
            hopped = false;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;

        double forward = (minecraft.options.keyUp.isDown() ? 1 : 0)
                - (minecraft.options.keyDown.isDown() ? 1 : 0);
        double strafe = (minecraft.options.keyLeft.isDown() ? 1 : 0)
                - (minecraft.options.keyRight.isDown() ? 1 : 0);
        boolean hasInput = forward != 0.0D || strafe != 0.0D;
        boolean onGround = player.onGround();

        if (Config.MOVEMENT_MOMENTUM_GLIDE.get()) {
            applyGlide(player, onGround, hasInput);
        }
        if (Config.MOVEMENT_AIR_CONTROL.get() && !onGround) {
            applyAirControl(player, forward, strafe);
        }
        if (Config.MOVEMENT_BUNNY_HOP.get()) {
            tryBunnyHop(player, onGround, minecraft.options.keyJump.isDown());
        }

        if (onGround) hopped = false;
    }

    /** 惯性滑行：记录正常移动速度，松键或落地后补回被原版摩擦削减的水平速度。 */
    private static void applyGlide(LocalPlayer player, boolean onGround, boolean hasInput) {
        Vec3 motion = player.getDeltaMovement();

        // 潜行时不保留惯性，避免蹲着滑出去
        if (player.isShiftKeyDown()) {
            glideX = 0.0D;
            glideZ = 0.0D;
            return;
        }

        // 地面有输入或滞空时，记录当前水平速度作为滑行储备
        if (!onGround || hasInput) {
            glideX = motion.x;
            glideZ = motion.z;
            return;
        }

        double speed = Math.hypot(glideX, glideZ);
        if (speed < GLIDE_MIN_SPEED) {
            glideX = 0.0D;
            glideZ = 0.0D;
            return;
        }

        player.setDeltaMovement(glideX * GLIDE_KEEP, motion.y, glideZ * GLIDE_KEEP);
        player.hasImpulse = true;
        glideX *= GLIDE_DECAY;
        glideZ *= GLIDE_DECAY;
    }

    /** 空中控制增强：朝输入方向缓慢转向，不额外提升速度。 */
    private static void applyAirControl(LocalPlayer player, double forward, double strafe) {
        if (forward == 0.0D && strafe == 0.0D) return;
        if (!isAirControllable(player)) return;

        double yaw = Math.toRadians(player.getYRot());
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        double dirX = strafe * cos - forward * sin;
        double dirZ = forward * cos + strafe * sin;
        double length = Math.hypot(dirX, dirZ);
        if (length < 1.0E-4D) return;
        dirX /= length;
        dirZ /= length;

        Vec3 motion = player.getDeltaMovement();
        double speed = Math.hypot(motion.x, motion.z);
        double newX;
        double newZ;
        if (speed < AIR_MIN_SPEED) {
            newX = motion.x + dirX * AIR_ACCEL;
            newZ = motion.z + dirZ * AIR_ACCEL;
        } else {
            newX = motion.x + (dirX * speed - motion.x) * AIR_TURN_RATE;
            newZ = motion.z + (dirZ * speed - motion.z) * AIR_TURN_RATE;
        }

        player.setDeltaMovement(newX, motion.y, newZ);
        player.hasImpulse = true;
    }

    /** 落地连跳：滞空下落且贴近地面时按下跳跃键，立刻重置垂直速度再次起跳。 */
    private static void tryBunnyHop(LocalPlayer player, boolean onGround, boolean jumpDown) {
        if (onGround || hopped || !jumpDown) return;
        if (!isAirControllable(player)) return;
        if (player.isShiftKeyDown()) return;

        Vec3 motion = player.getDeltaMovement();
        // 只在上升末段或下落阶段触发，避免与正常起跳冲突
        if (motion.y > 0.02D) return;
        if (groundGap(player) > HOP_GAP) return;

        double jumpPower = JUMP_POWER;
        MobEffectInstance boost = player.getEffect(MobEffects.JUMP);
        if (boost != null) {
            jumpPower += 0.1D * (boost.getAmplifier() + 1);
        }

        // 水平速度原样保留，因此不会出现落地停顿
        player.setDeltaMovement(motion.x, jumpPower, motion.z);
        player.fallDistance = 0.0F;
        player.hasImpulse = true;
        hopped = true;
    }

    /** 飞行、鞘翅、水中与攀爬状态下不接管移动。 */
    private static boolean isAirControllable(LocalPlayer player) {
        return !player.getAbilities().flying
                && !player.isFallFlying()
                && !player.isInWater()
                && !player.onClimbable();
    }

    /** 脚底到最近地面的距离，没有地面时返回一个很大的值。 */
    private static double groundGap(LocalPlayer player) {
        Vec3 start = player.position();
        Vec3 end = start.add(0.0D, -HOP_GAP, 0.0D);
        BlockHitResult hit = player.level().clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) return Double.MAX_VALUE;
        return start.y - hit.getLocation().y;
    }
}
