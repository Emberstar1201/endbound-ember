package com.emberstar.endbounember.entity;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * 本模组使用的标签。
 */
public class ModTags {
    public static class EntityTypes {
        /** 丧尸娘与溺尸娘的共同标签（同类，彼此不仇视） */
        public static final TagKey<EntityType<?>> ZOMBIE_GIRLS = TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "zombie_girls"));
        /** 丧尸娘的天敌：原版亡灵 */
        public static final TagKey<EntityType<?>> ZOMBIE_GIRL_ENEMIES = TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "zombie_girl_enemies"));
    }
}
