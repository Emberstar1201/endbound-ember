package com.emberstar.endbounember.world;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/**
 * 晨曦模板的战利品注入。
 * 不再限定具体结构：只要战利品表是宝箱表（路径以 chests/ 开头），
 * 就会往里面加一个 20% 概率触发、每次掉落 1 个晨曦模板的池子。
 */
@EventBusSubscriber(modid = EndboundEmber.MODID)
public final class DawnTemplateLoot {
    /** 单个宝箱刷出晨曦模板的概率。 */
    private static final float SPAWN_CHANCE = 0.2F;
    /** 原版与绝大多数模组的宝箱战利品表都放在 chests/ 路径下。 */
    private static final String CHEST_PATH_PREFIX = "chests/";
    /** 晨曦模板自身的宝箱战利品表，用作注入内容。 */
    private static final ResourceKey<LootTable> DAWN_TEMPLATE_TABLE = ResourceKey.create(
            Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(EndboundEmber.MODID, "chests/dawn_template"));

    private DawnTemplateLoot() {
    }

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        // 跳过自己，否则会给模板表注入它自己，生成时无限递归
        if (event.getName().equals(DAWN_TEMPLATE_TABLE.location())) return;
        if (!event.getName().getPath().startsWith(CHEST_PATH_PREFIX)) return;

        LootPool pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(NestedLootTable.lootTableReference(DAWN_TEMPLATE_TABLE))
                .when(LootItemRandomChanceCondition.randomChance(SPAWN_CHANCE))
                .build();
        event.getTable().addPool(pool);
    }
}
