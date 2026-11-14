package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.emberstar.endbounember.block.AcceleratorTorchBlockEntity;
import com.emberstar.endbounember.world.TimeHaloSkills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

/**
 * 时间暂停范围内的方块实体不再 tick：熔炉停止烧炼、漏斗停止搬运、刷怪笼停止旋转。
 * <p>与实体冻结同一片范围，服务端与客户端都生效。</p>
 * <p>同一处拦截还负责加速火把：范围内方块实体在每个世界 tick 里额外补跑几次 tick。</p>
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
        // 加速火把只在服务端生效，避免客户端方块实体被重复 tick 造成动画抖动
        if (!(level instanceof ServerLevel)) {
            return;
        }
        int extraTicks = AcceleratorTorchBlockEntity.extraTicksAt(level, ticker.getPos());
        for (int i = 0; i < extraTicks && !ticker.isRemoved(); i++) {
            ticker.tick();
        }
    }
}
