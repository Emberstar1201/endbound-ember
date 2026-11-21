package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.item.StardustWitchHaloEvents;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;

@Pseudo
@Mixin(targets = "com.Polarice3.Goety.common.entities.boss.Apostle", remap = false)
public abstract class GoetyApostleFlightMixin {
    @Redirect(
            method = "aiStep()V",
            slice = @Slice(
                    from = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z", opcode = org.objectweb.asm.Opcodes.GETFIELD),
                    to = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;flying:Z", opcode = org.objectweb.asm.Opcodes.PUTFIELD)),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isCreative()Z"),
            require = 0)
    private boolean endboundEmber$preserveHaloFlight(Player player) {
        // 使徒以 flying &= isCreative() 清除飞行状态；只替换这一次判断，保留玩家主动结束飞行的能力。
        return player.isCreative() || StardustWitchHaloEvents.wearingStardustHalo(player)
                || StardustWitchHaloEvents.wearingTimeHalo(player);
    }
}
