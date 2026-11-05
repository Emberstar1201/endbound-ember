package com.emberstar.endbounember.item;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

/** 时光光环：佩戴后按 I 键进出时间乱流维度。 */
public class TimeHaloItem extends ArmorItem {
    public TimeHaloItem(Holder<ArmorMaterial> material, Item.Properties properties) {
        super(material, Type.HELMET, properties.stacksTo(1));
    }
}
