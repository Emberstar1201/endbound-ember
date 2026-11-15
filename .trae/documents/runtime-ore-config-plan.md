# 原版矿石运行时调参计划

## Context
当前矿脉扩张通过 biome modifier 将固定 placed feature 加入群系生成设置；生成尝试次数保存在 JSON 中。用户希望由 OP 在游戏内调整原版矿石生成权重/密度，并且新设置只作用于此后新生成的区块。仅修改 ModConfigSpec 数值不能保证已构建的群系生成设置重新读取它，因此需要在实际矿石放置期间读取服务端参数。

## 推荐方案
采用静态注册的生成管线与运行时参数分离：保留 biome modifier 的群系/维度注册职责，新增可变参数的 PlacementModifier（若 API 无法安全动态调节尝试数，再采用自定义 Feature），在新区块进行矿石放置时从线程安全、服务端权威的参数状态读取当前值。避免运行期改写 `BiomeGenerationSettings` 或依赖 `/reload` 重建生物群系。

提供 OP（权限等级 2）专用的矿石配置查询/设置命令，明确参数控制的是生成尝试频率/密度，而不是矿脉体积；按矿物类别设置有边界的数值，限制生成开销。将运行期值存入服务端存档数据并在启动时从配置默认值初始化，确保重启后保留。保留现有配置开关作为总开关；现有 JSON 中的维度、群系和高度筛选尽量复用。

修改只影响命令设置后实际执行生成逻辑的区块；已生成区块不回填。明确正在生成或已排队区块的边界，并通过测试确认并行生成下读取状态的线程安全性。

## 关键文件
- `src/main/java/com/emberstar/endbounember/world/OreVeinExpansion.java`：注册与生成期参数读取的接入点。
- `src/main/java/com/emberstar/endbounember/world/ModCommands.java`：新增 OP 查询/设置命令，沿用 `.requires(source -> source.hasPermission(2))`。
- `src/main/java/com/emberstar/endbounember/Config.java`：仅承载合理默认值、边界和现有总开关。
- `src/main/java/com/emberstar/endbounember/world/`：放置参数状态及必要的存档持久化实现。
- `src/main/resources/data/endbound_ember/worldgen/placed_feature/` 与 `.../neoforge/biome_modifier/`：逐步将固定尝试数接到动态生成入口，保留现有过滤条件。
- `src/main/resources/assets/endbound_ember/lang/zh_cn.json`、`en_us.json`：如命令反馈使用翻译键则补充两种语言。

## 验证
- 编译并启动 NeoForge 1.21.1 开发服务器，验证世界生成资源与注册无错误。
- OP 可查询、设置参数；非 OP 无法执行；非法值在边界处拒绝。
- 调低/调高参数后，在新生成区域验证生成频率随之变化；已生成区块不改变。
- 重启服务器确认每世界参数持久化；检查并行新区块生成时没有崩溃或数据竞争。
- 明确功能控制的是尝试密度/频率，不声称直接改变单条矿脉大小或香草内部权重。
