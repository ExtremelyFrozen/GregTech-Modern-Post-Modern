---
title: Central Monitor（中央监视器）与 Placeholder（占位符）系统
---

### Central Monitor（中央监视器）

Central Monitor 是一个多方块结构，可以插入模块来渲染图片和文本。<br>
图片每 120 秒更新一次，文本更新速度取决于提供给该多方块结构的电压。
Central Monitor 使用指南：

1. 右键点击 Controller
2. 在 UI 中，你会看到一个由 Monitor 组成的网格，以及 Controller、Energy Hatch 和（可选的）Data Hatch
3. 左键选择一些 Monitor（任意形状都可以）
4. 点击 "Create group" 按钮
5. 你应当会在 UI 左侧看到一个 group，点击它会选中该 group 中的全部 Monitor，再次点击则取消选择
6. 点击要编辑的 group 名称旁边的齿轮图标
7. 此时会打开一个只有单个槽位的 UI，将模块放入该槽位（虽然可以放入一整组模块，但那没有任何实际效果）
8. 如果放入的是文本或图片模块，会出现一个新输入框，你可以在其中输入文本（图片模块则是一行 URL）
9. 输入文本后，点击槽位下方的绿色对勾，这会保存你输入的文本
10. 点击正在编辑的 group 旁边的齿轮图标，返回主菜单
11. 此时你应当能在 Central Monitor 上看到文本或图片

要移除一个 group，选中它并点击 "Remove from group"。要从 group 中移除单个 Monitor，只选中它并点击 "Remove from group"。
group 创建后不能再向其中添加 Monitor。图片尺寸由该 group 的左上角和右下角决定，
两者之间的方块必须属于同一个 group。Text Module 只会在其 group 的 Monitor 上显示文本。

!!! warning "Image Module 有一些 Bug，因此图片可能不会立即显示"

### Text Module（文本模块）

你可能已经注意到 Text Module 的 UI 中有一个数字输入框。它是文本缩放值，其中 1 表示 1/16 方块高度的行高。
你可能也注意到 Text Module 左侧还有一些额外槽位。
这些槽位由 Placeholder 引用，你可以在里面放入任意物品。大多数 Placeholder 还需要一个目标方块才能工作。要为 Monitor group 选择目标，
请在 Controller 的主 UI 中选中 group，右键点击要设为目标的方块，然后点击 "Set target"。如果想把 Central Monitor 外部的方块设为目标，
需要使用 Wireless Transmitter Cover。将它放在目标方块上，并用 Data Stick 右键点击它。然后将该 Data Stick 放入 Central Monitor 多方块结构中的 Data Hatch。
如果选择 Data Hatch 作为目标，你会看到一个新的数字输入框。输入 Data Stick 所在槽位的编号并点击 "Set target"。
目标会被设置为 Wireless Transmitter Cover 所在的方块。它可以跨维度工作。

!!! note "对于 Computer Monitor Cover，目标方块始终是该 Cover 放置在其上的方块。"

### Placeholders（占位符）

玩家可以在 Monitor Text Module 或 Computer Monitor Cover 中使用 Placeholder（后者的能力稍受限制）。
例如，玩家可以在 Text Module 中写入如下内容：
```
Hello on day {calc {tick} / 20000}!
Current energy buffer: {formatInt {energy}}/{formatInt {energyCapacity}} EU\
{if {cmp {energy} < 5000000} {color red "\nLOW ENERGY!"}}
Here's some random stuff:
{repeat 5 {repeat {random 2 10} {block}}
```
显示效果会类似这样：
```
Hello on day 420!
Current energy buffer: 4.2M/6.9M EU
LOW ENERGY!
Here's some random stuff:
███████
██
█████
████
██████████
```
这个系统是图灵完备的（也就是说，如果玩家真的想在 Central Monitor 上玩 Doom，理论上可以做到）。<br>
所有 Placeholder 都作用于字符串（更准确地说，是 `Component`，以支持文本格式），因此当你写下 `{calc {calc 2 + 4} * 3}` 时，
首先 `{calc 2 + 4}` 会被求值为 `6`，然后它会被转换成字符串，再转换回整数，接着传给第二个 Placeholder，
将 `{calc 6 * 3}` 求值为 `18`，最后再次转为字符串。这也允许像 `{calc 3 + 1}2` 这样的写法，其结果会是 `42`，
因为 Placeholder 外部的文本只是简单拼接在一起。Placeholder 参数以空格分隔；当你想把带空格的字符串传给 Placeholder 时，这可能有些麻烦，
例如 `{if 1 string with spaces}` 会导致错误。这种情况下可以使用双引号：`{if 1 "string with spaces"}` 可以正常工作。
有些 Placeholder 需要引用物品。为实现这一点，Text Module 的 UI 左侧提供了 8 个槽位。
通过与 Ender Item Links 交互，可以使用 `ender` Placeholder 自动向这些槽位插入或从中取出物品。<br>

!!! tip "完整的 Placeholder 列表、作用说明和用法示例可以在游戏内 Text Module 或 Computer Monitor UI 左侧查看。"
