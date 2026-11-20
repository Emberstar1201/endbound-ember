package com.emberstar.endbounember.world;

import com.emberstar.endbounember.Config;
import com.emberstar.endbounember.entity.ModEntities;
import com.emberstar.endbounember.item.DawnAmuletItem;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * 提升非 Boss 生物死亡和矿石方块的战利品数量，远古残骸和配置排除名单中的物品保持原数量。
 * 由服务端配置 vanillaLootBoostEnabled 控制开关。
 */
public class LootBoostModifier extends LootModifier {
    public static final double MULTIPLIER = 3.0D;

    public static final MapCodec<LootBoostModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, LootBoostModifier::new));

    public LootBoostModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!Config.VANILLA_LOOT_BOOST_ENABLED.get() || !isEligibleContext(context)) return generatedLoot;
        var excludedItems = Config.VANILLA_LOOT_BOOST_EXCLUDED_ITEMS.get();
        for (ItemStack stack : generatedLoot) {
            if (stack.isEmpty()) continue;
            if (excludedItems.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) continue;
            int boosted = (int) Math.round(stack.getCount() * MULTIPLIER);
            stack.setCount(Math.max(stack.getCount(), Math.min(stack.getMaxStackSize(), boosted)));
        }
        return generatedLoot;
    }

    private static boolean isEligibleContext(LootContext context) {
        if (!context.hasParam(LootContextParams.ORIGIN)) return false;

        // 方块战利品的 THIS_ENTITY 也可能是挖掘者，因此优先按方块状态判断。
        var blockState = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (blockState != null) {
            return context.hasParam(LootContextParams.TOOL)
                    && !context.hasParam(LootContextParams.DAMAGE_SOURCE)
                    && !blockState.is(Blocks.ANCIENT_DEBRIS)
                    && blockState.is(Tags.Blocks.ORES);
        }

        // ENCHANTED_DAMAGE 也有实体和伤害来源，额外含有附魔等级。
        if (!(context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof LivingEntity entity)
                || !context.hasParam(LootContextParams.DAMAGE_SOURCE)
                || context.hasParam(LootContextParams.ENCHANTMENT_LEVEL)) {
            return false;
        }

        // 暮色森林等模组先生成 Boss 死亡掉落，再装入奖励箱，因此必须在这里排除 Boss。
        return !entity.getType().is(Tags.EntityTypes.BOSSES)
                && !DawnAmuletItem.isBoss(entity)
                && entity.getType() != ModEntities.HEROBRINE.get();
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
