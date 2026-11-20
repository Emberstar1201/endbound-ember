package com.emberstar.endbounember.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;

/**
 * 黎明圣裁「判罚之雷」的落雷表现实体。
 *
 * <p>继承原版落雷并强制 {@code visualOnly}：只保留闪电、天空闪光与音效，
 * 不点燃方块、不伤害生物，因此也不会烧毁地上的掉落物。</p>
 */
public class DawnJudgementThunder extends LightningBolt {
    public DawnJudgementThunder(EntityType<? extends DawnJudgementThunder> type, Level level) {
        super(type, level);
        this.setVisualOnly(true);
    }
}
