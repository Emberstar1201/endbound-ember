# 末影龙经验 Mixin 启动崩溃修复计划

## Context

服务器启动时 `EnderDragonExperienceMixin` 的 `@Redirect` 报 `Scanned 0 target(s). No refMap loaded`，导致 Mixin 转换失败。Mixin 位于 `EnderDragon#tickDeath`，当前目标为 `ExperienceOrb.award(ServerLevel, Vec3, int)`；handler 参数对应此目标。当前 `endbound_ember.mixins.json` 未声明 refmap，`build.gradle` 中也未发现 Mixin 注解处理器/refmap 生成配置。尚未取得目标服务器对应字节码，因此不应猜测或直接替换方法描述符。

## 实施步骤

1. 先核对项目实际编译映射下 `EnderDragon#tickDeath` 的字节码，确认原版经验掉落调用是否存在、调用 owner 与完整 descriptor，并确认 `tickDeath` 的映射名。以项目的 Minecraft 1.21.1 / NeoForge 构建环境为准。
2. 依据字节码修正 [EnderDragonExperienceMixin.java](../../src/main/java/com/emberstar/endbounember/mixin/EnderDragonExperienceMixin.java) 中 `@Redirect.method`、`@At.target` 或 handler 参数；如果目标方法里没有预期调用，则按真实调用链调整实现，不使用 `require=0` 隐藏失效。
3. 为 [build.gradle](../../build.gradle) 中当前 ModDevGradle 2.0.148 构建补充兼容的 Mixin annotation processor/refmap 生成配置；在 [endbound_ember.mixins.json](../../src/main/resources/endbound_ember.mixins.json) 声明实际生成的 refmap 文件名。确认开发环境和发布 jar 都包含该资源。
4. 构建 jar 并检查产物：Mixin JSON 的 refmap 字段、refmap 文件存在且包含相关映射；以发布 jar 在服务器等效版本启动，确认不再出现 refmap 未加载和 Redirect 目标数为 0。

## 验证

- 执行 Gradle 编译及 jar 构建。
- 检查最终 jar 内 Mixin JSON 与 refmap 的路径和内容。
- 在 NeoForge 1.21.1 对应服务器环境验证启动；确认 Mixin 注入成功，并验证末影龙经验结算逻辑仍按原设计工作。
