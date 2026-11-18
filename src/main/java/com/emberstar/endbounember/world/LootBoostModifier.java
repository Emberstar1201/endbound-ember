package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * 原版战利品表掉落提升：把所有战利品表产出的物品数量按倍率放大。
 * 由服务端配置 vanillaLootBoostEnabled 控制开关。
 */
public class LootBoostModifier extends LootModifier {
    public static final double MULTIPLIER = 2.5D;

    public static final MapCodec<LootBoostModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, LootBoostModifier::new));

    public LootBoostModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!Config.VANILLA_LOOT_BOOST_ENABLED.get()) return generatedLoot;
        for (ItemStack stack : generatedLoot) {
            if (stack.isEmpty()) continue;
            int boosted = (int) Math.round(stack.getCount() * MULTIPLIER);
            stack.setCount(Math.max(stack.getCount(), Math.min(stack.getMaxStackSize(), boosted)));
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
