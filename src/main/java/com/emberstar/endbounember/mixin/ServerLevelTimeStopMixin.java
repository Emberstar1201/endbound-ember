package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.emberstar.endbounember.world.TimeHaloSkills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 时间暂停：落在暂停范围内的非玩家实体整 tick 跳过，
 * 既不动也不结算 AI，等价于被冻结；该范围内的区块随机刻也一并停住。
 * 玩家自身不受影响，弹射物保留原有动量，解除暂停后继续飞行。
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelTimeStopMixin {

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    private void endbound$freezeEntity(Entity entity, CallbackInfo ci) {
        if (TimeHaloSkills.isFrozen((ServerLevel) (Object) this, entity)) {
            ci.cancel();
        }
    }

    /** 冻结范围内的区块随机刻：作物、流体、降雪等不再推进 */
    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void endbound$freezeChunk(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        if (TimeHaloSkills.isChunkFrozen((ServerLevel) (Object) this, chunk.getPos())) {
            ci.cancel();
        }
    }
}
