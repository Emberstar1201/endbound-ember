# 第三方组件与致谢

本项目的部分实现参考了以下开源项目。依据其许可证要求，在此保留原始版权声明与许可证全文。

---

## Halo Mod

- 项目名称：Halo Mod（CurseForge 名称：Halo Customed）
- 作者：Azusa_Ke（AzusaKe）
- 仓库地址：<https://github.com/AzusaKe/Halo>
- 许可证：MIT License

### 本项目中的使用范围

星尘光环的渲染方式与物理表现参考自该项目，包括：

- 单个光环拆分为多张 PNG 图层、各图层独立配置尺寸、自转速度、公转轨道与浮动相位的分层绘制思路；
- 光环跟随实体头部、以头部坐标系为锚点定位的做法；
- 光环相对头部的偏移、倾斜与跟随表现。

上述内容在本项目中重新实现，未直接复制该项目的源代码文件。相关实现见：

- `src/main/java/com/emberstar/endbounember/client/HaloLayers.java`
- `src/main/java/com/emberstar/endbounember/EndboundEmberClient.java`

### 原始许可证全文

```text
MIT License

Copyright (c) 2025 Azusa_Ke

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

## Yes Steve Model（YSM）

- 项目名称：Yes Steve Model
- 作者：tartaric_acid（酒石酸菌）及 YSM 制作组
- 项目主页：<https://modrinth.com/mod/yes-steve-model>
- 许可证：**All Rights Reserved（保留所有权利，非开源）**

### 版本与许可证说明

YSM 存在两份不同的代码来源，本项目只针对其中一份做兼容，二者不可混为一谈：

- **Modrinth 发布版（本项目实际兼容的目标，如 2.6.5）**：许可证为 All Rights Reserved，核心加密与渲染部分以 C++ 编写，模型文件为加密二进制。
- **GitHub 重构仓库**（<https://github.com/YesSteveModel/YesSteveModel>）：采用 Apache License 2.0，但该仓库为尚未完成的重构版本，README 明确说明「当前公开版本还未完成」「所有公开格式、Schema、内部协议和缓存布局均尚未冻结」，**并非本项目兼容的发布版**。该仓库的许可证不适用于 Modrinth 分发的发布版。

因此，本项目**不主张** YSM 发布版适用任何开源许可证条款，也不适用 Apache License 2.0。

### 本项目中的使用范围

为确定兼容所需的运行时符号，本项目对 YSM **发布版 jar** 进行了字节码分析（反汇编），仅提取方法名、字段名、方法描述符与调用约定，用于判断 YSM 是否加载、以及在 YSM 接管玩家模型时读取头部骨骼姿态。具体而言：

- 分析产物仅限于混淆符号常量（见 `YsmSymbols.java`），未保留反编译所得的源代码；
- 未复制、未修改、未内嵌、未分发 YSM 的源代码、着色器、模型文件、动画文件、贴图或其他资源，本项目产物中不包含 YSM 的任何部分；
- 运行时通过 Java 反射与 Mixin 访问上述符号，不打包 YSM 的代码；
- 兼容层为可选软依赖：未安装 YSM 时相关 Mixin 不应用，光环自动回退到原版渲染路径。

相关实现见：

- `src/main/java/com/emberstar/endbounember/compat/ysm/`
- `src/main/resources/endbound_ember.ysm.mixins.json`

### 声明

本项目的 YSM 兼容功能为**非官方实现**，与 YSM 作者及制作组无任何隶属、合作或背书关系。YSM 的版权归其作者所有。若 YSM 作者对本兼容层的实现方式有异议，可通过项目仓库提出，我们会在后续版本中调整或移除相关功能。
