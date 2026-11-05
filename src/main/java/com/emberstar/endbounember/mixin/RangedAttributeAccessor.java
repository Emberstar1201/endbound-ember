package com.emberstar.endbounember.mixin;

import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 {@link RangedAttribute} 的属性上限，供 Herobrine 的 1500 点生命上限使用。
 */
@Mixin(RangedAttribute.class)
public interface RangedAttributeAccessor {
    @Mutable
    @Accessor("maxValue")
    void endboundEmber$setMaxValue(double maxValue);
}
