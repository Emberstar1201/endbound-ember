package com.emberstar.endbounember.entity;

import com.emberstar.endbounember.EndboundEmber;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体类型注册表。
 */
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, EndboundEmber.MODID);

    /** 丧尸娘 */
    public static final DeferredHolder<EntityType<?>, EntityType<ZombieGirl>> ZOMBIE_GIRL = ENTITY_TYPES.register("zombie_girl",
            () -> EntityType.Builder.of(ZombieGirl::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .build("zombie_girl"));

    /** 溺尸娘：使用真正独立的实体类型，继承丧尸娘的驯服与装备逻辑。 */
    public static final DeferredHolder<EntityType<?>, EntityType<DrownedGirl>> DROWNED_GIRL = ENTITY_TYPES.register("drowned_girl",
            () -> EntityType.Builder.of(DrownedGirl::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .build("drowned_girl"));

    public static final DeferredHolder<EntityType<?>, EntityType<HumanGirl>> HUMAN_GIRL = ENTITY_TYPES.register("human_girl",
            () -> EntityType.Builder.of(HumanGirl::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .build("human_girl"));

    /** 「刺破长夜的黎明」剑气。 */
    public static final DeferredHolder<EntityType<?>, EntityType<DawnSwordQiEntity>> DAWN_SWORD_QI =
            ENTITY_TYPES.register("dawn_sword_qi",
                    () -> EntityType.Builder.<DawnSwordQiEntity>of(DawnSwordQiEntity::new, MobCategory.MISC)
                            .sized(0.6F, 0.6F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build("dawn_sword_qi"));
}
