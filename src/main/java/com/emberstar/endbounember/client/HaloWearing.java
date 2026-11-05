package com.emberstar.endbounember.client;

import com.emberstar.endbounember.EndboundEmber;
import com.emberstar.endbounember.compat.CuriosCompat;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** 客户端佩戴光环判定，供成就界面、提示条与 HUD 共用。 */
public final class HaloWearing {
    private HaloWearing() {
    }

    /** 是否戴着星尘光环（头盔栏或饰品栏均可） */
    public static boolean stardustHalo(@Nullable Player player) {
        if (player == null) return false;
        Item halo = EndboundEmber.STARDUST_WITCH_HALO.get();
        if (player.getItemBySlot(EquipmentSlot.HEAD).is(halo)) return true;
        return ModList.get().isLoaded("curios") && CuriosCompat.isEquipped(player, halo);
    }
}
