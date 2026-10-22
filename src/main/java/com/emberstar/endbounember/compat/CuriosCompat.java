package com.emberstar.endbounember.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

/**
 * 仅在 Curios 已加载时才会被类加载器解析，避免未安装 Curios 时出现链接错误。
 */
public final class CuriosCompat {
    private CuriosCompat() {
    }

    public static boolean isEquipped(LivingEntity entity, Item item) {
        return CuriosApi.getCuriosInventory(entity)
                .map(ICuriosItemHandler::getCurios)
                .map(curios -> curios.values().stream()
                        .anyMatch(handler -> handler.getStacks().getSlots() > 0
                                && containsItem(handler, item)))
                .orElse(false);
    }

    private static boolean containsItem(
            top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler handler,
            Item item) {
        var stacks = handler.getStacks();
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
            if (stacks.getStackInSlot(slot).is(item)) {
                return true;
            }
        }
        return false;
    }
}
