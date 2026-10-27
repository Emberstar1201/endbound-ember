package com.emberstar.endbounember.item;

import java.util.List;
import java.util.Map;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, EndboundEmber.MODID);

    // 星尘魔女的光环：护甲值 34 / 盔甲韧性 24 / 击退抗性 10（100% 免疫击退），layers 为空表示不渲染原版护甲层
    public static final Holder<ArmorMaterial> STARDUST_WITCH_HALO = ARMOR_MATERIALS.register(
            "stardust_witch_halo", () -> new ArmorMaterial(
                    Map.of(ArmorItem.Type.HELMET, 34),
                    0,
                    SoundEvents.ARMOR_EQUIP_LEATHER,
                    () -> Ingredient.EMPTY,
                    List.of(),
                    24.0F,
                    10.0F));

    private ModArmorMaterials() {
    }
}
