# Endbound Ember

Endbound Ember 是一个面向 Minecraft 1.21.1 NeoForge官方重置官方重置 Enchant-expansion 的原版拓展模组。

## 1.0.0

### 生物

- 新增丧尸娘（Zombie Girl）。
- 新增溺尸娘（Drowned Girl）。
- 新增幸存者少女（Human Girl）。
- 丧尸娘与溺尸娘使用 Alex/细手玩家模型和 64×64 双层皮肤。
- 支持 `zombie_girl.png`、`zombie_girl_1.png` 至 `zombie_girl_19.png`。
- 支持 `human_girl.png`、`human_girl_1.png` 至 `human_girl_19.png`。
- 幼年体沿用对应编号皮肤。

### 战斗与行为

- 丧尸娘与溺尸娘属于亡灵生物，并保留亡灵杀手附魔的额外伤害关系。
- 不会主动攻击玩家、友好生物、村民或同类。
- 具有增强的生命值、攻击伤害和移动速度。
- 支持近战、弓、弩和三叉戟战斗。
- 弓箭会从伙伴背包中消耗箭矢，并支持战斗中回收未命中的箭。
- 溺尸娘可以在水中下沉、游泳，并根据环境尝试上岸。
- 野生个体在没有敌人时会自主漫步。
- 低血量时可能逃离非玩家造成的伤害来源。

### 驯服、背包与装备

- 可使用腐肉和生肉驯服丧尸娘与溺尸娘。
- 驯服后右键打开伙伴背包。
- 提供 6 个装备槽和 64 格伙伴背包。
- 前四个装备槽分别对应头盔、胸甲、护腿和靴子。
- 装备提供原版属性与附魔效果，但不会在实体模型上渲染护甲。
- 溺尸娘生成时携带三叉戟。

### 治愈与感染

- 使用生命之星右键丧尸娘或溺尸娘可启动治愈过程。
- 治愈过程包含僵尸村民治愈音效、绿色粒子和白色烟雾粒子阶段。
- 治愈完成后转换为幸存者少女，并保留对应皮肤编号、主人、装备和背包。
- 幸存者少女受到亡灵生物攻击并降至半血或以下时会重新感染。
- 溺尸娘治愈出的幸存者少女感染后会变回溺尸娘。

### 生成

- 丧尸娘和溺尸娘拥有独立的附近玩家补刷逻辑。
- 补刷范围限制在玩家附近，并设置数量上限与远距离清理，避免无限积累造成卡顿。
- 和平难度也允许特殊补刷。
- 高亮度区域仍有机会生成丧尸娘。

## 纹理放置

将实体纹理放入：

```text
src/main/resources/assets/endbound_ember/textures/entity/
```

将物品纹理放入：

```text
src/main/resources/assets/endbound_ember/textures/item/
```

## 开发环境

- Minecraft 1.21.1
- NeoForge 21.1.262
- Java 21
- Gradle Wrapper

## 许可证

本项目使用 GNU General Public License v3.0（GPLv3）。完整许可证文本见 [LICENSE](LICENSE)。

Minecraft、NeoForge 及其相关资产属于各自权利人。本项目不声称拥有 Mojang 或 NeoForged 的商标及官方资产所有权。

<span style="color:red">**⚠️ 特殊提醒（免责声明）：丧尸娘的受伤音频极度社死，请不要在公共场合播放！若真的要玩，请戴好耳机！**</span>

<span style="color:red">**⚠️ WARNING (Disclaimer): The Zombie Girl's hurt sound is EXTREMELY embarrassing. Do NOT play it out loud in public! If you really want to play, please wear headphones!**</span>

<span style="color:orange">**⚠️ 音频警告：模组中铃铛的声音存在余音效果，佩戴耳机时可能对听力造成潜在伤害，请注意适当调节音量。**</span>

<span style="color:orange">**⚠️ Audio Warning: The bell sounds in this mod have a lingering resonance effect. When wearing headphones, it may cause potential hearing damage. Please adjust the volume appropriately.**</span>
## 与我互动

- 📺 **哔哩哔哩投稿**：可以投稿到我的个人主页：<https://b23.tv/6agWJVH>
- 💖 **爱发电赞助**（若对你有帮助，欢迎支持一下）：
  - 赞助链接：<https://www.ifdian.net/a/Emberstar>
  - 爱发电主页：<https://www.ifdian.net/a/Emberstar>

---

## 🙏 致谢

**制作参与**：Shasnow

**额外感谢**：Wildcanyon，感谢提供丧尸娘材质。 [哔哩哔哩主页](https://space.bilibili.com/622498204?spm_id_from=333.788.upinfo.head.click)

**内测人员**：Shixiangye、Shasnow、andan_、鸿銞

**赞助者**（排名不分先后）：

| 赞助者 | 金额    |
|--------|---------|
| andan_ | 23.14元 |
| 鸿銞 | 15元    |
| 。乄夜幽魂。☆ | 5 元    |
| 丝羽珍 | 14.5 元 |
|@我是💩，我写的东西全是？ | 5 元 |

**反馈人员**：Shasnow、andan_、鸿銞、Shixiangye

感谢内测群友的反馈与支持，以及朋友们的脑洞投稿。

---

## AI 中转站宣传 开发模型来源

- AI 中转站官网：<https://rcolaapi.top/>
- 运营者：Zch
- 中转站群：**272395232**
