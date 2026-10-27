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
