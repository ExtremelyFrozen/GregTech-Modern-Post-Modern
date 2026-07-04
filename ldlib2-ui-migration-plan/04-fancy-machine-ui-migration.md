# 04. Fancy Machine UI 迁移

## 目标

迁移 GTM 高级机器 UI 框架，使普通机器、多方块机器、side tabs、configurator、tooltip panel 和机器预览能在 LDLib2 UI 上运行。

## 当前结构

关键入口：

- `IFancyUIMachine`
- `FancyMachineUIWidget`
- `IFancyUIProvider`
- `TabsWidget`
- `VerticalTabsWidget`
- `PageSwitcher`
- `TitleBarWidget`
- `ConfiguratorPanel`
- `TooltipsPanel`
- `IFancyConfigurator`
- `IFancyConfiguratorButton`

当前 `IFancyUIMachine.createUI(...)` 直接创建旧 `ModularUI`，并挂载 `FancyMachineUIWidget`。

## 目标结构

建议拆成五个层次：

1. shell
   - 顶层窗口。
   - 标题栏。
   - 背景。
   - 玩家背包区域。
2. main page
   - 当前机器主页面。
   - 由 `createUIWidget()` 或 editable UI 生成。
3. side tabs
   - 主 tab。
   - recipe mode tab。
   - directional configurator tab。
   - cover configurator tab。
4. configurator panel
   - 按钮、selector、输入框、状态提示。
5. tooltip panel
   - machine tooltip。
   - trait tooltip。
   - cover tooltip。

## `IFancyUIMachine` 迁移

### 保留语义

应保留：

- `createMainPage(...)`
- `createUIWidget()`
- `attachSideTabs(...)`
- `attachConfigurators(...)`
- `attachTooltips(...)`
- `getTabIcon()`
- `getTabTooltips()`
- `getTitle()`

### 改变返回类型

旧返回类型：

- `ModularUI`
- `Widget`
- `WidgetGroup`

迁移后建议：

- 对外用 GTM 自有 `GTUIElement` 或直接 LDLib2 `UIElement`。
- 不让业务层返回旧 `Widget`。

### 默认机器预览

旧默认预览：

- `TrackedDummyWorld`
- `BlockInfo.fromBlockState(...)`
- `SceneWidget`
- 自动旋转 camera

迁移目标：

- LDLib2 `Scene` UIElement。
- 保留 dummy world 或等价 preview world。
- 保留自动旋转。
- 保留 ortho / fov / render facing / selection 配置。

## Tabs 迁移

### 当前职责

tabs 负责：

- 主机器 tab。
- 多 recipe type 的模式 tab。
- 方向配置 tab。
- cover config tab。
- page switch。
- active tab 状态。

### 迁移策略

把 tab 状态拆成：

- 本地 UI 状态：当前显示哪个 tab。
- 服务端状态：机器模式、方向配置、cover 配置。

本地 UI 状态不需要同步。

服务端状态必须走 GT sync：

- 模式字段用 `@SyncToServer` 或 `@SyncBoth`。
- 一次性按钮用 GT sync action。

### 验收

- tab 切换不发无意义网络包。
- 修改机器模式时服务端状态变化。
- 修改方向配置时能保存并同步。
- cover tab 通过 `UICoverHolder` 操作 cover。

## Configurator 迁移

### 控件分类

Configurator 中常见控件：

- toggle button。
- icon button。
- enum selector。
- number input。
- text input。
- mouse wheel action。
- middle click action。
- custom client action。

### 同步分类

迁移时必须给每个交互分类：

| 交互 | 同步方式 |
| --- | --- |
| 展开/折叠 panel | 本地 UI 状态 |
| tab 切换 | 本地 UI 状态 |
| 开关机器工作 | `@SyncToServer` 或 GT action |
| 修改 enum 模式 | `@SyncToServer` / `@SyncBoth` |
| 数字输入 commit | `@SyncToServer` / `@SyncBoth` |
| 清空配置 | GT sync action |
| 滚轮增减 | 字段同步或 GT sync action |
| 中键重置 | GT sync action |

### 设计建议

为 configurator 提供统一 event adapter：

- 本地事件先进入 GTM adapter。
- adapter 判断交互类型。
- 字段型交互写 sync 字段。
- 动作型交互发送 GT action。

这样可以防止业务控件直接调用 LDLib2 RPC。

## Tooltips 迁移

Tooltip 来源：

- machine definition。
- machine trait。
- cover。
- recipe condition。
- slot/tank。
- configurator button。

迁移策略：

- 静态 tooltip 直接绑定 LDLib2 tooltip。
- 动态 tooltip 从同步字段或客户端可读状态生成。
- 不能为了 tooltip 向服务端发即时 RPC。

## Editable UI 和 `.rtui`

Fancy UI 当前可能从 `editableUI` 创建自定义 UI。

迁移建议：

- 主迁移阶段先支持代码生成的默认 UI。
- `.rtui` 反序列化留到后续阶段。
- 如果必须保留 `.rtui`，应写一次性转换器，而不是在运行期同时维护旧 widget 和新 element。

## 验收清单

- `IFancyUIMachine` 默认 UI 可打开。
- 默认机器预览正常渲染和旋转。
- title bar、tab、configurator、tooltip 显示正常。
- tab 本地切换不触发服务端同步。
- 修改真实机器状态只走 GT sync。
- 不存在 LDLib2 RPC 调用。
