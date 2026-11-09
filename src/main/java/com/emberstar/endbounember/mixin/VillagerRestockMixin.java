package com.emberstar.endbounember.mixin;

import com.emberstar.endbounember.Config;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Villager.class)
public abstract class VillagerRestockMixin {
    @Shadow private long lastRestockGameTime;
    @Shadow private long lastRestockCheckDayTime;
    @Shadow private int numberOfRestocksToday;
    @Shadow private void resetNumberOfRestocks() {}
    @Shadow private boolean allowedToRestock() { return false; }
    @Shadow private boolean needsToRestock() { return false; }

    /** @author Endbound Ember @reason 1-2 game-day trade restock interval */
    @Overwrite
    public boolean shouldRestock() {
        Villager villager = (Villager) (Object) this;
        long now = villager.level().getGameTime();
        long delay = Config.BETTER_VILLAGER_TRADING.get() ? 6000L : 12000L;
        boolean elapsed = now > this.lastRestockGameTime + delay;
        long dayTime = villager.level().getDayTime();
        if (this.lastRestockCheckDayTime > 0L) {
            elapsed |= dayTime / 24000L > this.lastRestockCheckDayTime / 24000L;
        }
        this.lastRestockCheckDayTime = dayTime;
        if (elapsed) {
            this.lastRestockGameTime = now;
            this.resetNumberOfRestocks();
        }
        return this.allowedToRestock() && this.needsToRestock();
    }
}
