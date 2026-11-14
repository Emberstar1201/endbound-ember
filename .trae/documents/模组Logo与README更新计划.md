# 模组 Logo 与 README 更新计划

## 背景

项目已有独立的 `logo_mod.png` 与 `icon_mod.png`。用户希望模组菜单使用 Logo，而启动器/GitHub README 使用模组图标，并同步更新中英文 README。当前 `neoforge.mods.toml` 仍将 `icon_mod.png` 配置为模组 Logo，README 未展示图片，当前 Gradle 版本为 `1.2.1 beta-2.5`，但 README 的版本介绍仍停留在历史版本。

## 实施方案

1. 修改 `src/main/templates/META-INF/neoforge.mods.toml`，将 `logoFile` 改为 `logo_mod.png`，注释说明其用于模组菜单展示。
2. 更新根目录 `README.md` 和 `README_EN.md`：在顶部展示 `logo_mod.png`；以 `icon_mod.png` 作为仓库/启动器场景的图标；补充当前版本 `1.2.1 beta-2.5` 与测试版状态；整理功能摘要，使其与当前实现相符。旧版本说明作为历史记录保留，不将历史版本误写成当前版本。不添加未经源码确认的功能描述。
3. 保持 Gradle 资源配置不变；`src/main/resources` 已是资源根，两张 PNG 会打包至 JAR 根目录。

## 验证

- 执行 `processResources`，检查生成的 `META-INF/neoforge.mods.toml` 中 `logoFile` 指向 `logo_mod.png`。
- 检查构建资源或 JAR 根目录同时包含 `logo_mod.png` 与 `icon_mod.png`。
- 核对中英文 README 图片相对路径、版本状态和主要功能说明一致。
- 检查 GitHub Markdown 预览中的图片显示与页面排版；构建后确认模组菜单使用 Logo。

## 关键文件

- `src/main/templates/META-INF/neoforge.mods.toml`
- `README.md`
- `README_EN.md`
- `gradle.properties`（仅读取当前版本）
