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

    public static boolean isEquippedInSlot(LivingEntity entity, String slotId, Item item) {
        return CuriosApi.getCuriosInventory(entity)
                .map(ICuriosItemHandler::getCurios)
                .map(curios -> {
                    var handler = curios.get(slotId);
                    return handler != null && containsItem(handler, item);
                })
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

    /**
     * 取走饰品栏里所有满足条件的物品，返回取下来的副本。
     * 用于万灵之主夺走玩家的饰品。
     */
    public static java.util.List<net.minecraft.world.item.ItemStack> extractMatching(
            LivingEntity entity, java.util.function.Predicate<net.minecraft.world.item.ItemStack> predicate) {
        java.util.List<net.minecraft.world.item.ItemStack> extracted = new java.util.ArrayList<>();
        CuriosApi.getCuriosInventory(entity).ifPresent(inventory -> {
            for (var handler : inventory.getCurios().values()) {
                var stacks = handler.getStacks();
                for (int slot = 0; slot < stacks.getSlots(); slot++) {
                    var stack = stacks.getStackInSlot(slot);
                    if (!stack.isEmpty() && predicate.test(stack)) {
                        extracted.add(stack.copy());
                        stacks.setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
                    }
                }
            }
        });
        return extracted;
    }
}
