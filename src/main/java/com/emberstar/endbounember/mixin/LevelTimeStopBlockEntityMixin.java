package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.emberstar.endbounember.world.TimeHaloSkills;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

/**
 * 时间暂停范围内的方块实体不再 tick：熔炉停止烧炼、漏斗停止搬运、刷怪笼停止旋转。
 * <p>与实体冻结同一片范围，服务端与客户端都生效。</p>
 */
@Mixin(Level.class)
public abstract class LevelTimeStopBlockEntityMixin {

    @Redirect(method = "tickBlockEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V"))
    private void endbound$tickBlockEntity(TickingBlockEntity ticker) {
        Level level = (Level) (Object) this;
        if (TimeHaloSkills.isBlockFrozen(level, ticker.getPos())) {
            return;
        }
        ticker.tick();
    }
}
