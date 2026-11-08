# 自定义 BOSS 音频任意 OGG 文件名修复计划

## Context

TCL 实例日志与导入目录显示：资源包只检查固定文件名，而玩家的 OGG 使用了其他文件名，因此加载时继续播放 mod 默认曲目。当前 `HerobrineBossMusic` 还包含未完成的候选扫描代码，缺少必要 import 和 `readSeconds(Path)` 实现。目标是保持仅接受 `.ogg` 扩展名、允许任意文件名，并确保实际播放曲目与自动循环时长来源一致。

## 推荐实现

1. 在 `HerobrineBossMusic` 补齐 OGG 文件扫描依赖和文件时长读取方法；扫描导入目录内 `.ogg` 文件（扩展名大小写不敏感），按最近修改时间选择一个候选。
2. 在 `BossMusicPack.addPack` 中，于创建/注册资源包前，将候选文件复制到资源包固定引用路径 `assets/endbound_ember/sounds/music/herobrine_boss_music.ogg` 并覆盖旧副本。无候选时移除旧副本，使资源加载回退到 mod 内置曲目。
3. 循环时长只解析固定有效副本；无导入时解析 mod 内置音频，解析失败才用配置兜底，避免计时与正在播放的音轨不一致。
4. 将四种语言配置提示改为支持任意文件名 OGG，并说明多文件时的选择规则以及 `F3+T` 重载方法。
5. 保持 `sounds.json` 和声音事件 ID 不变；不改变已有音乐服务端触发逻辑。

## 关键文件

- `src/main/java/com/emberstar/endbounember/client/BossMusicPack.java`
- `src/main/java/com/emberstar/endbounember/entity/HerobrineBossMusic.java`
- `src/main/resources/assets/endbound_ember/lang/zh_cn.json`
- `src/main/resources/assets/endbound_ember/lang/zh_tw.json`
- `src/main/resources/assets/endbound_ember/lang/zh_hk.json`
- `src/main/resources/assets/endbound_ember/lang/en_us.json`

## 验证

1. 运行 `./gradlew.bat compileJava --console=plain -q` 和 `./gradlew.bat build --console=plain -q`。
2. 检查最终 jar 含音乐相关类及原版默认曲目资源。
3. 在 TCL 实例中确认资源包注册前生成固定名称副本，且副本与被选中的任意命名 OGG 哈希一致。
4. 检查最新启动日志确认导入曲目被识别；按 `F3+T` 重载后在 BOSS 范围内试听。
5. 删除导入 OGG 后重载，确认旧副本被清除并恢复默认曲目；验证循环时长取自当前有效曲目。

## 边界

构建可在本地完成，但最终声音覆盖需在 TCL 实例重新加载资源并进入 BOSS 范围实测。多名 OGG 时默认采用最近修改的文件。