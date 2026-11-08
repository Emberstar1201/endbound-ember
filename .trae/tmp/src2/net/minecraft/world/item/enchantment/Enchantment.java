package net.minecraft.world.item.enchantment;

import com.google.common.collect.Maps;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.effects.DamageImmunity;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableFloat;

public record Enchantment(Component description, Enchantment.EnchantmentDefinition definition, HolderSet<Enchantment> exclusiveSet, DataComponentMap effects) {
    public static final int MAX_LEVEL = 255;
    public static final Codec<Enchantment> DIRECT_CODEC = RecordCodecBuilder.create(
        p_344998_ -> p_344998_.group(
                    ComponentSerialization.CODEC.fieldOf("description").forGetter(Enchantment::description),
                    Enchantment.EnchantmentDefinition.CODEC.forGetter(Enchantment::definition),
                    RegistryCodecs.homogeneousList(Registries.ENCHANTMENT)
                        .optionalFieldOf("exclusive_set", HolderSet.direct())
                        .forGetter(Enchantment::exclusiveSet),
                    EnchantmentEffectComponents.CODEC.optionalFieldOf("effects", DataComponentMap.EMPTY).forGetter(Enchantment::effects)
                )
                .apply(p_344998_, Enchantment::new)
    );
    public static final Codec<Holder<Enchantment>> CODEC = RegistryFixedCodec.create(Registries.ENCHANTMENT);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<Enchantment>> STREAM_CODEC = ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT);

    public static Enchantment.Cost constantCost(int cost) {
        return new Enchantment.Cost(cost, 0);
    }

    public static Enchantment.Cost dynamicCost(int base, int perLevel) {
        return new Enchantment.Cost(base, perLevel);
    }

    public static Enchantment.EnchantmentDefinition definition(
        HolderSet<Item> supportedItems,
        HolderSet<Item> primaryItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        EquipmentSlotGroup... slots
    ) {
        return new Enchantment.EnchantmentDefinition(
            supportedItems, Optional.of(primaryItems), weight, maxLevel, minCost, maxCost, anvilCost, List.of(slots)
        );
    }

    public static Enchantment.EnchantmentDefinition definition(
        HolderSet<Item> supportedItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        EquipmentSlotGroup... slots
    ) {
        return new Enchantment.EnchantmentDefinition(supportedItems, Optional.empty(), weight, maxLevel, minCost, maxCost, anvilCost, List.of(slots));
    }

    /**
     * Creates a new map containing all items equipped by an entity in {@linkplain #slots slots that the enchantment cares about}. These items are not tested for having the enchantment.
     *
     * @param entity The entity to collect equipment for.
     */
    public Map<EquipmentSlot, ItemStack> getSlotItems(LivingEntity entity) {
        Map<EquipmentSlot, ItemStack> map = Maps.newEnumMap(EquipmentSlot.class);

        for (EquipmentSlot equipmentslot : EquipmentSlot.values()) {
            if (this.matchingSlot(equipmentslot)) {
                ItemStack itemstack = entity.getItemBySlot(equipmentslot);
                if (!itemstack.isEmpty()) {
                    map.put(equipmentslot, itemstack);
                }
            }
        }

        return map;
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#supportsEnchantment(Holder)}
     */
    @Deprecated
    public HolderSet<Item> getSupportedItems() {
        return this.definition.supportedItems();
    }

    public boolean matchingSlot(EquipmentSlot slot) {
        return this.definition.slots().stream().anyMatch(p_345027_ -> p_345027_.test(slot));
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#isPrimaryItemFor(Holder)} This method
     *             does not respect {@link ItemStack#supportsEnchantment(Holder)}
     *             since the {@link Holder} is not available, which makes the result
     *             of calling it invalid.
     */
    @Deprecated
    public boolean isPrimaryItem(ItemStack stack) {
        return this.isSupportedItem(stack) && (this.definition.primaryItems.isEmpty() || stack.is(this.definition.primaryItems.get()));
    }

    /**
     * @deprecated Neo: Use {@link ItemStack#supportsEnchantment(Holder)}
     */
    @Deprecated
    public boolean isSupportedItem(ItemStack item) {
        return item.is(this.definition.supportedItems);
    }

    public int getWeight() {
        return this.definition.weight();
    }

    public int getAnvilCost() {
        return this.definition.anvilCost();
    }

    public int getMinLevel() {
        return 1;
    }

    public int getMaxLevel() {
        return this.definition.maxLevel();
    }

    /**
     * Returns the minimal value of enchantability needed on the enchantment level passed.
     */
    public int getMinCost(int level) {
        return this.definition.minCost().calculate(level);
    }

    public int getMaxCost(int level) {
        return this.definition.maxCost().calculate(level);
    }

    @Override
    public String toString() {
        return "Enchantment " + this.description.getString();
    }

    public static boolean areCompatible(Holder<Enchantment> first, Holder<Enchantment> second) {
        return !first.equals(second) && !first.value().exclusiveSet.contains(second) && !second.value().exclusiveSet.contains(first);
    }

    public static Component getFullname(Holder<Enchantment> enchantment, int level) {
        MutableComponent mutablecomponent = enchantment.value().description.copy();
        if (enchantment.is(EnchantmentTags.CURSE)) {
            ComponentUtils.mergeStyles(mutablecomponent, Style.EMPTY.withColor(ChatFormatting.RED));
        } else {
            ComponentUtils.mergeStyles(mutablecomponent, Style.EMPTY.withColor(ChatFormatting.GRAY));
        }

        if (level != 1 || enchantment.value().getMaxLevel() != 1) {
            mutablecomponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + level));
        }

        return mutablecomponent;
    }

    /**
     * Checks if the enchantment can be applied to a given ItemStack.
     *
     * @param stack The ItemStack to test.
     * @deprecated Neo: Use {@link ItemStack#supportsEnchantment(Holder)}
     */
    @Deprecated
    public boolean canEnchant(ItemStack stack) {
        return this.definition.supportedItems().contains(stack.getItemHolder());
    }

    public <T> List<T> getEffects(DataComponentType<List<T>> component) {
        return this.effects.getOrDefault(component, List.of());
    }

    public boolean isImmuneToDamage(ServerLevel level, int enchantmentLevel, Entity entity, DamageSource damageSource) {
        LootContext lootcontext = damageContext(level, enchantmentLevel, entity, damageSource);

        for (ConditionalEffect<DamageImmunity> conditionaleffect : this.getEffects(EnchantmentEffectComponents.DAMAGE_IMMUNITY)) {
            if (conditionaleffect.matches(lootcontext)) {
                return true;
            }
        }

        return false;
    }

    public void modifyDamageProtection(
        ServerLevel level, int enchantmentLevel, ItemStack stack, Entity entity, DamageSource damageSource, MutableFloat damageProtection
    ) {
        LootContext lootcontext = damageContext(level, enchantmentLevel, entity, damageSource);

        for (ConditionalEffect<EnchantmentValueEffect> conditionaleffect : this.getEffects(EnchantmentEffectComponents.DAMAGE_PROTECTION)) {
            if (conditionaleffect.matches(lootcontext)) {
                damageProtection.setValue(conditionaleffect.effect().process(enchantmentLevel, entity.getRandom(), damageProtection.floatValue()));
            }
        }
    }

    public void modifyDurabilityChange(ServerLevel level, int enchantmentLevel, ItemStack tool, MutableFloat durabilityChange) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.ITEM_DAMAGE, level, enchantmentLevel, tool, durabilityChange);
    }

    public void modifyAmmoCount(ServerLevel level, int enchantmentLevel, ItemStack tool, MutableFloat ammoCount) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.AMMO_USE, level, enchantmentLevel, tool, ammoCount);
    }

    public void modifyPiercingCount(ServerLevel level, int enchantmentLevel, ItemStack tool, MutableFloat piercingCount) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.PROJECTILE_PIERCING, level, enchantmentLevel, tool, piercingCount);
    }

    public void modifyBlockExperience(ServerLevel level, int enchantmentLevel, ItemStack tool, MutableFloat blockExperience) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.BLOCK_EXPERIENCE, level, enchantmentLevel, tool, blockExperience);
    }

    public void modifyMobExperience(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat mobExperience) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.MOB_EXPERIENCE, level, enchantmentLevel, tool, entity, mobExperience);
    }

    public void modifyDurabilityToRepairFromXp(ServerLevel level, int enchantmentLevel, ItemStack tool, MutableFloat durabilityToRepairFromXp) {
        this.modifyItemFilteredCount(EnchantmentEffectComponents.REPAIR_WITH_XP, level, enchantmentLevel, tool, durabilityToRepairFromXp);
    }

    public void modifyTridentReturnToOwnerAcceleration(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat tridentReturnToOwnerAcceleration) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.TRIDENT_RETURN_ACCELERATION, level, enchantmentLevel, tool, entity, tridentReturnToOwnerAcceleration);
    }

    public void modifyTridentSpinAttackStrength(RandomSource random, int enchantmentLevel, MutableFloat value) {
        this.modifyUnfilteredValue(EnchantmentEffectComponents.TRIDENT_SPIN_ATTACK_STRENGTH, random, enchantmentLevel, value);
    }

    public void modifyFishingTimeReduction(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat fishingTimeReduction) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.FISHING_TIME_REDUCTION, level, enchantmentLevel, tool, entity, fishingTimeReduction);
    }

    public void modifyFishingLuckBonus(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat fishingLuckBonus) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.FISHING_LUCK_BONUS, level, enchantmentLevel, tool, entity, fishingLuckBonus);
    }

    public void modifyDamage(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, DamageSource damageSource, MutableFloat damage) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.DAMAGE, level, enchantmentLevel, tool, entity, damageSource, damage);
    }

    public void modifyFallBasedDamage(
        ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, DamageSource damageSource, MutableFloat fallBasedDamage
    ) {
        this.modifyDamageFilteredValue(
            EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK, level, enchantmentLevel, tool, entity, damageSource, fallBasedDamage
        );
    }

    public void modifyKnockback(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, DamageSource damageSource, MutableFloat knockback) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.KNOCKBACK, level, enchantmentLevel, tool, entity, damageSource, knockback);
    }

    public void modifyArmorEffectivness(
        ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, DamageSource damageSource, MutableFloat armorEffectiveness
    ) {
        this.modifyDamageFilteredValue(EnchantmentEffectComponents.ARMOR_EFFECTIVENESS, level, enchantmentLevel, tool, entity, damageSource, armorEffectiveness);
    }

    public static void doPostAttack(
        TargetedConditionalEffect<EnchantmentEntityEffect> effect,
        ServerLevel level,
        int enchantmentLevel,
        EnchantedItemInUse item,
        Entity p_entity,
        DamageSource damageSource
    ) {
        if (effect.matches(damageContext(level, enchantmentLevel, p_entity, damageSource))) {
            Entity entity = switch (effect.affected()) {
                case ATTACKER -> damageSource.getEntity();
                case DAMAGING_ENTITY -> damageSource.getDirectEntity();
                case VICTIM -> p_entity;
            };
            if (entity != null) {
                effect.effect().apply(level, enchantmentLevel, item, entity, entity.position());
            }
        }
    }

    public void doPostAttack(
        ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, EnchantmentTarget target, Entity entity, DamageSource damageSource
    ) {
        for (TargetedConditionalEffect<EnchantmentEntityEffect> targetedconditionaleffect : this.getEffects(EnchantmentEffectComponents.POST_ATTACK)) {
            if (target == targetedconditionaleffect.enchanted()) {
                doPostAttack(targetedconditionaleffect, level, enchantmentLevel, item, entity, damageSource);
            }
        }
    }

    public void modifyProjectileCount(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat projectileCount) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.PROJECTILE_COUNT, level, enchantmentLevel, tool, entity, projectileCount);
    }

    public void modifyProjectileSpread(ServerLevel level, int enchantmentLevel, ItemStack tool, Entity entity, MutableFloat projectileSpread) {
        this.modifyEntityFilteredValue(EnchantmentEffectComponents.PROJECTILE_SPREAD, level, enchantmentLevel, tool, entity, projectileSpread);
    }

    public void modifyCrossbowChargeTime(RandomSource random, int enchantmentLevel, MutableFloat value) {
        this.modifyUnfilteredValue(EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME, random, enchantmentLevel, value);
    }

    public void modifyUnfilteredValue(DataComponentType<EnchantmentValueEffect> componentType, RandomSource random, int enchantmentLevel, MutableFloat value) {
        EnchantmentValueEffect enchantmentvalueeffect = this.effects.get(componentType);
        if (enchantmentvalueeffect != null) {
            value.setValue(enchantmentvalueeffect.process(enchantmentLevel, random, value.floatValue()));
        }
    }

    public void tick(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.TICK),
            entityContext(level, enchantmentLevel, entity, entity.position()),
            p_345592_ -> p_345592_.apply(level, enchantmentLevel, item, entity, entity.position())
        );
    }

    public void onProjectileSpawned(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.PROJECTILE_SPAWNED),
            entityContext(level, enchantmentLevel, entity, entity.position()),
            p_346231_ -> p_346231_.apply(level, enchantmentLevel, item, entity, entity.position())
        );
    }

    public void onHitBlock(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 pos, BlockState state) {
        applyEffects(
            this.getEffects(EnchantmentEffectComponents.HIT_BLOCK),
            blockHitContext(level, enchantmentLevel, entity, pos, state),
            p_346325_ -> p_346325_.apply(level, enchantmentLevel, item, entity, pos)
        );
    }

    public void modifyItemFilteredCount(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> componentType,
        ServerLevel level,
        int enchantmentLevel,
        ItemStack tool,
        MutableFloat value
    ) {
        applyEffects(
            this.getEffects(componentType),
            itemContext(level, enchantmentLevel, tool),
            p_347300_ -> value.setValue(p_347300_.process(enchantmentLevel, level.getRandom(), value.getValue()))
        );
    }

    public void modifyEntityFilteredValue(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> componentType,
        ServerLevel level,
        int enchantmentLevel,
        ItemStack tool,
        Entity entity,
        MutableFloat value
    ) {
        applyEffects(
            this.getEffects(componentType),
            entityContext(level, enchantmentLevel, entity, entity.position()),
            p_347312_ -> value.setValue(p_347312_.process(enchantmentLevel, entity.getRandom(), value.floatValue()))
        );
    }

    public void modifyDamageFilteredValue(
        DataComponentType<List<ConditionalEffect<EnchantmentValueEffect>>> componentType,
        ServerLevel level,
        int enchantmentLevel,
        ItemStack tool,
        Entity entity,
        DamageSource damageSource,
        MutableFloat value
    ) {
        applyEffects(
            this.getEffects(componentType),
            damageContext(level, enchantmentLevel, entity, damageSource),
            p_347304_ -> value.setValue(p_347304_.process(enchantmentLevel, entity.getRandom(), value.floatValue()))
        );
    }

    public static LootContext damageContext(ServerLevel level, int enchantmentLevel, Entity entity, DamageSource damageSource) {
        LootParams lootparams = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, entity.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, damageSource.getDirectEntity())
            .create(LootContextParamSets.ENCHANTED_DAMAGE);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static LootContext itemContext(ServerLevel level, int enchantmentLevel, ItemStack tool) {
        LootParams lootparams = new LootParams.Builder(level)
            .withParameter(LootContextParams.TOOL, tool)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .create(LootContextParamSets.ENCHANTED_ITEM);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static LootContext locationContext(ServerLevel level, int enchantmentLevel, Entity entity, boolean enchantmentActive) {
        LootParams lootparams = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, entity.position())
            .withParameter(LootContextParams.ENCHANTMENT_ACTIVE, enchantmentActive)
            .create(LootContextParamSets.ENCHANTED_LOCATION);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static LootContext entityContext(ServerLevel level, int enchantmentLevel, Entity entity, Vec3 origin) {
        LootParams lootparams = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, origin)
            .create(LootContextParamSets.ENCHANTED_ENTITY);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static LootContext blockHitContext(ServerLevel level, int enchantmentLevel, Entity entity, Vec3 origin, BlockState state) {
        LootParams lootparams = new LootParams.Builder(level)
            .withParameter(LootContextParams.THIS_ENTITY, entity)
            .withParameter(LootContextParams.ENCHANTMENT_LEVEL, enchantmentLevel)
            .withParameter(LootContextParams.ORIGIN, origin)
            .withParameter(LootContextParams.BLOCK_STATE, state)
            .create(LootContextParamSets.HIT_BLOCK);
        return new LootContext.Builder(lootparams).create(Optional.empty());
    }

    public static <T> void applyEffects(List<ConditionalEffect<T>> effects, LootContext context, Consumer<T> applier) {
        for (ConditionalEffect<T> conditionaleffect : effects) {
            if (conditionaleffect.matches(context)) {
                applier.accept(conditionaleffect.effect());
            }
        }
    }

    public void runLocationChangedEffects(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, LivingEntity entity) {
        if (item.inSlot() != null && !this.matchingSlot(item.inSlot())) {
            Set<EnchantmentLocationBasedEffect> set1 = entity.activeLocationDependentEnchantments().remove(this);
            if (set1 != null) {
                set1.forEach(p_352862_ -> p_352862_.onDeactivated(item, entity, entity.position(), enchantmentLevel));
            }
        } else {
            Set<EnchantmentLocationBasedEffect> set = entity.activeLocationDependentEnchantments().get(this);

            for (ConditionalEffect<EnchantmentLocationBasedEffect> conditionaleffect : this.getEffects(EnchantmentEffectComponents.LOCATION_CHANGED)) {
                EnchantmentLocationBasedEffect enchantmentlocationbasedeffect = conditionaleffect.effect();
                boolean flag = set != null && set.contains(enchantmentlocationbasedeffect);
                if (conditionaleffect.matches(locationContext(level, enchantmentLevel, entity, flag))) {
                    if (!flag) {
                        if (set == null) {
                            set = new ObjectArraySet<>();
                            entity.activeLocationDependentEnchantments().put(this, set);
                        }

                        set.add(enchantmentlocationbasedeffect);
                    }

                    enchantmentlocationbasedeffect.onChangedBlock(level, enchantmentLevel, item, entity, entity.position(), !flag);
                } else if (set != null && set.remove(enchantmentlocationbasedeffect)) {
                    enchantmentlocationbasedeffect.onDeactivated(item, entity, entity.position(), enchantmentLevel);
                }
            }

            if (set != null && set.isEmpty()) {
                entity.activeLocationDependentEnchantments().remove(this);
            }
        }
    }

    public void stopLocationBasedEffects(int enchantmentLevel, EnchantedItemInUse item, LivingEntity entity) {
        Set<EnchantmentLocationBasedEffect> set = entity.activeLocationDependentEnchantments().remove(this);
        if (set != null) {
            for (EnchantmentLocationBasedEffect enchantmentlocationbasedeffect : set) {
                enchantmentlocationbasedeffect.onDeactivated(item, entity, entity.position(), enchantmentLevel);
            }
        }
    }

    public static Enchantment.Builder enchantment(Enchantment.EnchantmentDefinition definition) {
        return new Enchantment.Builder(definition);
    }

//    TODO: Reimplement. Not sure if we want to patch EnchantmentDefinition or hack this in as an EnchantmentEffectComponent.
//    /**
//     * Is this enchantment allowed to be enchanted on books via Enchantment Table
//     * @return false to disable the vanilla feature
//     */
//    public boolean isAllowedOnBooks() {
//        return true;
//    }

    public static class Builder {
        private final Enchantment.EnchantmentDefinition definition;
        private HolderSet<Enchantment> exclusiveSet = HolderSet.direct();
        private final Map<DataComponentType<?>, List<?>> effectLists = new HashMap<>();
        private final DataComponentMap.Builder effectMapBuilder = DataComponentMap.builder();

        /**
         * Neo: Allow customizing or changing the {@link Component} created by the enchantment builder.
         */
        protected java.util.function.UnaryOperator<MutableComponent> nameFactory = java.util.function.UnaryOperator.identity();

        public Builder(Enchantment.EnchantmentDefinition definition) {
            this.definition = definition;
        }

        public Enchantment.Builder exclusiveWith(HolderSet<Enchantment> exclusiveSet) {
            this.exclusiveSet = exclusiveSet;
            return this;
        }

        public <E> Enchantment.Builder withEffect(DataComponentType<List<ConditionalEffect<E>>> componentType, E effect, LootItemCondition.Builder requirements) {
            this.getEffectsList(componentType).add(new ConditionalEffect<>(effect, Optional.of(requirements.build())));
            return this;
        }

        public <E> Enchantment.Builder withEffect(DataComponentType<List<ConditionalEffect<E>>> componentType, E effect) {
            this.getEffectsList(componentType).add(new ConditionalEffect<>(effect, Optional.empty()));
            return this;
        }

        public <E> Enchantment.Builder withEffect(
            DataComponentType<List<TargetedConditionalEffect<E>>> componentType,
            EnchantmentTarget enchanted,
            EnchantmentTarget affected,
            E effect,
            LootItemCondition.Builder requirements
        ) {
            this.getEffectsList(componentType).add(new TargetedConditionalEffect<>(enchanted, affected, effect, Optional.of(requirements.build())));
            return this;
        }

        public <E> Enchantment.Builder withEffect(
            DataComponentType<List<TargetedConditionalEffect<E>>> componentType, EnchantmentTarget enchanted, EnchantmentTarget affected, E effect
        ) {
            this.getEffectsList(componentType).add(new TargetedConditionalEffect<>(enchanted, affected, effect, Optional.empty()));
            return this;
        }

        public Enchantment.Builder withEffect(DataComponentType<List<EnchantmentAttributeEffect>> componentType, EnchantmentAttributeEffect effect) {
            this.getEffectsList(componentType).add(effect);
            return this;
        }

        public <E> Enchantment.Builder withSpecialEffect(DataComponentType<E> component, E value) {
            this.effectMapBuilder.set(component, value);
            return this;
        }

        public Enchantment.Builder withEffect(DataComponentType<Unit> componentType) {
            this.effectMapBuilder.set(componentType, Unit.INSTANCE);
            return this;
        }

        /**
         * Allows specifying an operator that can customize the default {@link Component} created by {@link #build(ResourceLocation)}.
         *
         * @return this
         */
        public Enchantment.Builder withCustomName(java.util.function.UnaryOperator<MutableComponent> nameFactory) {
            this.nameFactory = nameFactory;
            return this;
        }

        private <E> List<E> getEffectsList(DataComponentType<List<E>> componentType) {
            return (List<E>)this.effectLists.computeIfAbsent(componentType, p_346247_ -> {
                ArrayList<E> arraylist = new ArrayList<>();
                this.effectMapBuilder.set(componentType, arraylist);
                return arraylist;
            });
        }

        public Enchantment build(ResourceLocation location) {
            return new Enchantment(
                // Neo: permit custom name components instead of a single hardcoded translatable component.
                this.nameFactory.apply(Component.translatable(Util.makeDescriptionId("enchantment", location))),
                this.definition, this.exclusiveSet, this.effectMapBuilder.build()
            );
        }
    }

    public static record Cost(int base, int perLevelAboveFirst) {
        public static final Codec<Enchantment.Cost> CODEC = RecordCodecBuilder.create(
            p_345979_ -> p_345979_.group(
                        Codec.INT.fieldOf("base").forGetter(Enchantment.Cost::base),
                        Codec.INT.fieldOf("per_level_above_first").forGetter(Enchantment.Cost::perLevelAboveFirst)
                    )
                    .apply(p_345979_, Enchantment.Cost::new)
        );

        public int calculate(int level) {
            return this.base + this.perLevelAboveFirst * (level - 1);
        }
    }

    public static record EnchantmentDefinition(
        HolderSet<Item> supportedItems,
        Optional<HolderSet<Item>> primaryItems,
        int weight,
        int maxLevel,
        Enchantment.Cost minCost,
        Enchantment.Cost maxCost,
        int anvilCost,
        List<EquipmentSlotGroup> slots
    ) {
        public static final MapCodec<Enchantment.EnchantmentDefinition> CODEC = RecordCodecBuilder.mapCodec(
            p_344890_ -> p_344890_.group(
                        RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("supported_items").forGetter(Enchantment.EnchantmentDefinition::supportedItems),
                        RegistryCodecs.homogeneousList(Registries.ITEM)
                            .optionalFieldOf("primary_items")
                            .forGetter(Enchantment.EnchantmentDefinition::primaryItems),
                        ExtraCodecs.intRange(1, 1024).fieldOf("weight").forGetter(Enchantment.EnchantmentDefinition::weight),
                        ExtraCodecs.intRange(1, 255).fieldOf("max_level").forGetter(Enchantment.EnchantmentDefinition::maxLevel),
                        Enchantment.Cost.CODEC.fieldOf("min_cost").forGetter(Enchantment.EnchantmentDefinition::minCost),
                        Enchantment.Cost.CODEC.fieldOf("max_cost").forGetter(Enchantment.EnchantmentDefinition::maxCost),
                        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("anvil_cost").forGetter(Enchantment.EnchantmentDefinition::anvilCost),
                        EquipmentSlotGroup.CODEC.listOf().fieldOf("slots").forGetter(Enchantment.EnchantmentDefinition::slots)
                    )
                    .apply(p_344890_, Enchantment.EnchantmentDefinition::new)
        );
    }
}
