package com.emberstar.endbounember.item;

import javax.annotation.Nullable;

import com.emberstar.endbounember.entity.DawnArrowEntity;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class DawnArrowItem extends ArrowItem {
    public DawnArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack ammo, LivingEntity shooter, @Nullable ItemStack weapon) {
        return new DawnArrowEntity(level, shooter, ammo.copyWithCount(1), weapon);
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        DawnArrowEntity arrow = new DawnArrowEntity(level, pos, stack.copyWithCount(1));
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        return arrow;
    }
}
