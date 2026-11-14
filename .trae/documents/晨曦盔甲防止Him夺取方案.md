# 晨曦盔甲防止 Him 夺取方案

## Context

Him 的“万界之主”形态会触发装备夺取。当前晨曦头盔、胸甲、护腿和靴子没有加入统一保护判断，因此可能被夺走。目标是让四件晨曦盔甲在所有现有夺取路径中都保留。

## 实施方案

1. 仅修改 `Herobrine.java` 的 `isPlunderProtected(ItemStack)`。
2. 在现有固定保护条件中加入 `EndboundEmber.DAWN_HELMET`、`DAWN_CHESTPLATE`、`DAWN_LEGGINGS`、`DAWN_BOOTS`。
3. 使用现有 `ItemStack#is` 判断风格，不改物品注册和 Him 夺取流程。
4. 依赖统一保护函数覆盖装备槽、主副手、背包和 Curios 夺取路径；不增加配置项或额外状态。

## 验证

- 运行 `./gradlew.bat compileJava --no-configuration-cache -q`。
- 静态确认四件晨曦盔甲均经过 `isPlunderProtected` 判断。
- 游戏内以万界之主形态测试四件装备，确认它们不会被移除；同时确认普通未保护物品仍可被夺取。
