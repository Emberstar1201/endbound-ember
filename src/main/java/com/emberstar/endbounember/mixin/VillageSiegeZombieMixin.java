package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.world.ZombieSiegeTracker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(VillageSiege.class)
public abstract class VillageSiegeZombieMixin {
    /** 在围城僵尸真正加入世界前登记 UUID，用于精确统计本次围城的击杀归属。 */
    @Redirect(method = "trySpawn",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntityWithPassengers(Lnet/minecraft/world/entity/Entity;)V"))
    private void endboundEmber$trackSiegeZombie(ServerLevel level, Entity entity) {
        if (entity instanceof Zombie zombie) {
            ZombieSiegeTracker.registerSiegeZombie(level, zombie);
        }
        level.addFreshEntityWithPassengers(entity);
    }
}
