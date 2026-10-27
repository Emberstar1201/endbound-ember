package com.emberstar.endbounember.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

public class StardustWitchHaloItem extends ArmorItem {
    public StardustWitchHaloItem(Holder<ArmorMaterial> material, Item.Properties properties) {
        super(material, Type.HELMET, properties.stacksTo(1));
    }
}
