package com.emberstar.endbounember.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.emberstar.endbounember.client.TimeFreezeClient;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;

/**
 * 时间暂停范围内的粒子不再推进：火焰、烟尘、雨雪都会定格在半空，
 * 解除暂停后继续按原寿命与速度演化。
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineTimeStopMixin {

    @Inject(method = "tickParticle", at = @At("HEAD"), cancellable = true)
    private void endbound$freezeParticle(Particle particle, CallbackInfo ci) {
        if (TimeFreezeClient.isFrozen(particle.getPos())) {
            ci.cancel();
        }
    }
}
