# 03. 控件与布局迁移

## 目标

把 GTM 旧 `Widget` / `WidgetGroup` 控件树迁移到 LDLib2 `UIElement`，同时尽量保留 GTM 自有控件 API，降低业务调用点改动量。

## 映射总表

| 旧 LDLib / GTM 控件 | 迁移目标 | 备注 |
| --- | --- | --- |
| `Widget` | `UIElement` | 自定义控件改为继承或组合 LDLib2 element |
| `WidgetGroup` | `UIElement` container | 第一阶段可用 fixed layout wrapper |
| `ButtonWidget` | `Button` | 点击事件转 GT sync 或本地 UI 状态 |
| `LabelWidget` | `Label` | 注意 text component 和 tooltip |
| `TextFieldWidget` | `TextField` | 输入提交转字段同步或 action |
| `SelectorWidget` / `EnumSelectorWidget` | `Selector` | enum 绑定保留 GTM 语义 |
| `SlotWidget` | `ItemSlot` | 重点保留 handler、tooltip、XEI 语义 |
| `TankWidget` | `FluidSlot` | 重点保留 fluid handler、bucket click、amount |
| `ProgressWidget` | LDLib2 progress element 或 GTM 自定义 element | recipe progress 需要兼容 XEI |
| `ImageWidget` | LDLib2 image/texture element | 贴图统一进 style 或 texture value |
| `SceneWidget` | `Scene` | 机器预览迁移到 LDLib2 Scene |

## 布局策略

### 第一阶段：保留绝对坐标语义

现有 GTM 大量 UI 使用：

- `new WidgetGroup(x, y, width, height)`
- `setSelfPosition(...)`
- `setSize(...)`
- `addSelfPosition(...)`
- slot 以 18px 网格排列

第一阶段不应强制调用点全部改为 Flex。建议在 GTM wrapper 中提供：

- fixed position container
- fixed size element
- absolute child placement
- legacy pixel coordinate adapter

这样可以先完成 API 迁移，再逐步重排布局。

### 第二阶段：迁移可复用区域到 Taffy/Flex

适合迁移到 Flex 的区域：

- tab 列表
- configurator 按钮列表
- tooltip panel
- player inventory 模板
- recipe input/output slot group
- scroll list

不建议第一轮就 Flex 化的区域：

- 复杂机器纹理 overlay。
- 依赖精确像素位置的 recipe UI。
- 旧 `.rtui` 自定义 UI。

### 第三阶段：引入 LSS 样式

可以逐步把以下内容迁到 LSS：

- 背景纹理。
- slot 边框。
- button hover/pressed 状态。
- tab active/inactive 状态。
- font color。
- spacing。
- progress texture。

注意：

- 迁移早期不要把行为逻辑放进样式。
- 样式只处理视觉与布局，业务状态仍来自 GTM machine/cover/item。

## `SlotWidget` 迁移

### 当前职责

`SlotWidget` 当前不仅是物品槽，还承担：

- `IItemHandlerModifiable` slot 绑定。
- vanilla `Container` slot 绑定。
- can take / can put 限制。
- hover overlay。
- tooltip。
- player inventory location info。
- XEI ingredient 获取。
- EMI/JEI clickable ingredient 包装。
- cycle item entry 展示。

### 迁移目标

迁移到 LDLib2 `ItemSlot`，但保留 GTM `SlotWidget` facade。

建议拆分职责：

- `GTItemSlotElement`：LDLib2 element 实现。
- `GTSlotBinding`：handler/container 绑定信息。
- `GTSlotXEIAdapter`：XEI ingredient 逻辑。
- `GTSlotInteraction`：can take/can put 和点击限制。

### XEI 注意点

旧 `IngredientIO.RENDER_ONLY` 不应照搬。迁移时应明确：

- recipe input -> `INPUT`
- recipe output -> `OUTPUT`
- circuit/tool/catalyst -> `CATALYST`
- 纯展示 -> `NONE`

### 验收

- 玩家背包 slot 正常显示和交互。
- 机器输入输出 slot 限制不变。
- phantom/cycle slot 正常显示。
- JEI/EMI/REI 鼠标悬停 ingredient 正确。
- chance tooltip 不丢失。

## `TankWidget` 迁移

### 当前职责

`TankWidget` 当前承担：

- fluid handler 绑定。
- tank index。
- amount 显示。
- fill direction。
- hover tooltip。
- bucket click 填充/抽取。
- cached client fluid/capacity。
- XEI fluid ingredient。
- EMI/JEI clickable fluid 包装。
- `detectAndSendChanges()` 风格的数据更新触发。

### 迁移目标

迁移到 LDLib2 `FluidSlot`，保留 GTM `TankWidget` facade。

建议拆分职责：

- `GTFluidSlotElement`：LDLib2 element 实现。
- `GTFluidSlotBinding`：handler 和 tank index。
- `GTFluidSlotInteraction`：bucket click。
- `GTFluidXEIAdapter`：fluid ingredient。
- `GTFluidAmountFormatter`：amount 文本和 tooltip。

### 同步约束

客户端点击 tank 后：

- 如果只是本地 hover 或 tooltip，不同步。
- 如果改变服务端 fluid handler，必须走 GT sync 或现有服务端交互路径。
- 不允许用 LDLib2 RPC 直接调用服务端方法。

### 验收

- tank 显示方向一致。
- fluid amount 文本一致。
- bucket click 行为一致。
- fluid tooltip 一致。
- Recipe Viewer fluid ingredient 正确。

## Button/Selector/TextField 迁移

### Button

迁移目标：

- 旧 `ButtonWidget` 到 LDLib2 `Button`。
- 点击事件先判断是本地状态、字段同步还是动作同步。

分类：

- 只切 tab：本地 UI 状态。
- 改机器开关：`@SyncToServer` 或 `@SyncBoth`。
- 执行一次命令：GT sync action。

### Selector

迁移目标：

- enum selector 到 LDLib2 `Selector`。
- 当前值来自 machine/cover/item 字段。
- 变更时写字段并触发 GT sync。

### TextField

迁移目标：

- 数字输入和文本输入到 LDLib2 `TextField`。
- 输入过程中可本地缓存。
- commit 时校验并同步。

错误处理：

- 非法输入应在 UI 层拒绝或显示原值。
- 不能把非法 payload 发到服务端后静默忽略。
- 服务端仍需校验。

## Progress 和 Image 迁移

### Progress

Recipe progress 和机器 progress 应区分：

- Recipe Viewer progress 通常使用固定 supplier。
- 运行中机器 progress 来自同步字段或 trait 状态。

迁移策略：

- 先建立 GTM progress element。
- 保留 fill direction。
- 保留 texture group。
- 保留 progress id 绑定规则。

### Image

迁移策略：

- 静态贴图迁到 LDLib2 texture element 或 LSS。
- 动态 overlay 保留为 GTM custom element。
- 旧 `IGuiTexture` 到 LDLib2 texture value 需要集中适配。

## 验收清单

- 旧控件 facade 能覆盖现有业务调用点。
- 基础机器 UI 无明显布局偏移。
- 所有 slot/tank 交互不丢失。
- 所有按钮交互分类明确。
- 所有客户端到服务端交互都能追溯到 GT sync。
- 不新增 LDLib2 RPC 依赖。
