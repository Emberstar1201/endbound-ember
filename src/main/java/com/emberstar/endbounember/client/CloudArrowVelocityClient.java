package com.emberstar.endbounember.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;

/**
 * 客户端：把服务端同步过来的真实速度写回箭矢实体。
 */
public final class CloudArrowVelocityClient {
    private CloudArrowVelocityClient() {
    }

    public static void apply(int entityId, double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity instanceof AbstractArrow arrow) {
            arrow.setDeltaMovement(x, y, z);
        }
    }
}
