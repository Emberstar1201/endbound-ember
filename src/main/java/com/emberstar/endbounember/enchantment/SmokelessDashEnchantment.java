package com.emberstar.endbounember.enchantment;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 翱翔：胸甲（鞘翅）专属，最高 I 级。
 * 鞘翅滑翔时无需烟花火箭即可持续获得推进力，疾跑状态下推进更强。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class SmokelessDashEnchantment {
    /** 每 tick 的基础推进力。 */
    private static final double THRUST = 0.045D;
    /** 疾跑时的额外推进倍率。 */
    private static final double SPRINT_BONUS = 1.8D;
    /** 水平速度上限，避免无限叠加。 */
    private static final double MAX_HORIZONTAL_SPEED = 1.6D;
    /** 垂直速度上限。 */
    private static final double MAX_VERTICAL_SPEED = 1.0D;

    private SmokelessDashEnchantment() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !Config.SMOKELESS_DASH_ENABLED.get()) return;
        if (!player.isFallFlying()) return;

        Vec3 look = player.getLookAngle();
        double thrust = THRUST * (player.isSprinting() ? SPRINT_BONUS : 1.0D);
        Vec3 motion = player.getDeltaMovement().add(look.scale(thrust));

        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        if (horizontal > MAX_HORIZONTAL_SPEED) {
            double scale = MAX_HORIZONTAL_SPEED / horizontal;
            motion = new Vec3(motion.x * scale, motion.y, motion.z * scale);
        }
        if (motion.y > MAX_VERTICAL_SPEED) {
            motion = new Vec3(motion.x, MAX_VERTICAL_SPEED, motion.z);
        }

        player.setDeltaMovement(motion);
        player.hurtMarked = true;
    }
}
