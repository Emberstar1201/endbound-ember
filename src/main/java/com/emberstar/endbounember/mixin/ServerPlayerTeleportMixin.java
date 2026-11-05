package com.emberstar.endbounember.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.emberstar.endbounember.world.TimeTurbulenceAccess;
import com.emberstar.endbounember.world.TimeTurbulenceLevels;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;

/**
 * 拦住跨维度传送进入他人时间乱流维度的玩家。
 * <p>
 * 玩家自身按 I 键进出走的是 6 参 {@code teleportTo}，不会经过这里；
 * 只有 {@code /tp} 等跨维度传送会命中，因此挂起申请后把这次传送取消，
 * 待维度内的玩家点击 [允许] 后再由 {@link TimeTurbulenceLevels#enterAsGuest} 送入。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {
    @Inject(
            method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void endboundEmber$interceptTimeTurbulence(ServerLevel level, double x, double y, double z,
            Set<RelativeMovement> relativeMovements, float yRot, float xRot, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (level == self.level() || !TimeTurbulenceLevels.isTimeTurbulence(level)) {
            return;
        }
        if (TimeTurbulenceAccess.interceptTeleport(self, level, x, y, z, yRot, xRot)) {
            cir.setReturnValue(false);
        }
    }
}
