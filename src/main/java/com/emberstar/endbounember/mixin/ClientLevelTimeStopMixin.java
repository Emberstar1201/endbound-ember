package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.emberstar.endbounember.client.TimeFreezeClient;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

/**
 * 时间暂停在客户端同样生效：范围内的实体不再 tick。
 * <p>服务端本就冻结这些实体，如果客户端继续自行模拟，位置会与服务器分叉，
 * 解除暂停时再被服务端位置拽回去，看起来就是弹射物瞬移。这里直接跳过整只实体的 tick，
 * 位置、动画与粒子表现都会停在暂停那一刻。</p>
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelTimeStopMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void endbound$freezeEntity(Entity entity, CallbackInfo ci) {
        if (TimeFreezeClient.isEntityFrozen(entity)) {
            ci.cancel();
        }
    }
}
