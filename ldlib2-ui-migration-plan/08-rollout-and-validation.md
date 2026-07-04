# 08. 推进顺序与验证

## 推荐推进顺序

### 1. 基线盘点

产出：

- 旧 LDLib UI import 清单。
- `createUI(...)` / `createUIWidget()` 清单。
- 旧 widget 构造清单。
- GT sync 交互清单。
- `.rtui` 资源清单。

验收：

- 能明确哪些文件属于入口、控件、Fancy UI、Recipe UI、Cover UI、Item UI。

### 2. UI 打开链路

优先级：

1. Machine UI。
2. Item/Tool UI。
3. Cover UI with `UICoverHolder`。
4. UI Editor 后置。

验收：

- UI 能打开和关闭。
- holder 解析正确。
- holder 失效路径明确。
- 未引入 LDLib2 RPC。

### 3. 基础控件

优先级：

1. `SlotWidget`
2. `TankWidget`
3. Button
4. Selector
5. TextField
6. Progress
7. Image

验收：

- 普通机器 UI 可以由 LDLib2 element 组成。
- slot/tank 交互不回归。
- 控件事件全部分类为本地、字段同步或 action。

### 4. Fancy UI

优先级：

1. `FancyMachineUIWidget`
2. title bar
3. tabs
4. configurator panel
5. tooltip panel
6. Scene 预览

验收：

- 默认机器 UI 可用。
- tab 切换正常。
- configurator 能修改机器/cover 状态。
- Scene 预览正常渲染。

### 5. 机器和 Cover 调用点

优先级：

1. 基础 machine feature。
2. storage machine。
3. steam machine。
4. multiblock machine。
5. cover。
6. item/tool。
7. AE2 integration。

验收：

- 每类至少选一个代表 UI 做手动测试。
- 交互数据能在服务端保存。
- 客户端重新打开 UI 后状态一致。

### 6. Recipe/XEI

优先级：

1. 自动布局 recipe UI。
2. item slot。
3. fluid slot。
4. progress。
5. JEI。
6. EMI。
7. REI。
8. `.rtui` 后置。

验收：

- Recipe Viewer 中 input/output/catalyst 角色正确。
- ingredient 点击和 tooltip 正确。
- chance 正确。

## 验证矩阵

| 模块 | 编译 | UI 打开 | 字段同步 | 动作同步 | 视觉回归 | Recipe Viewer |
| --- | --- | --- | --- | --- | --- | --- |
| Machine UI entry | 必须 | 必须 | 必须 | 视情况 | 基础 | 不涉及 |
| Cover UI entry | 必须 | 必须 | 必须 | 必须 | 基础 | 不涉及 |
| Item UI entry | 必须 | 必须 | 视情况 | 视情况 | 基础 | 不涉及 |
| SlotWidget | 必须 | 必须 | 必须 | 视情况 | 必须 | 必须 |
| TankWidget | 必须 | 必须 | 必须 | 视情况 | 必须 | 必须 |
| Fancy UI | 必须 | 必须 | 必须 | 必须 | 必须 | 不涉及 |
| Recipe UI | 必须 | 不涉及 | 不涉及 | 不涉及 | 必须 | 必须 |

## 手动测试建议

### 机器 UI

- 打开基础电力机器。
- 打开 steam machine。
- 打开 storage machine。
- 打开 multiblock controller。
- 切换 machine mode。
- 开关 working enabled。
- 输入数字配置。
- 关闭并重新打开。

### Cover UI

- 打开一个 cover UI。
- 修改 cover 配置。
- 关闭并重新打开。
- 拆除 cover 后观察 UI。
- 替换 cover 后观察旧 UI 是否失效。
- 发送无效 side action 时服务端拒绝并记录日志。

### Item/Tool UI

- 主手打开。
- 副手打开。
- 打开后切换手持物品。
- 修改 item 配置并保存。

### Recipe Viewer

- 打开普通 recipe。
- 打开 fluid recipe。
- 打开 steam recipe。
- 检查 input/output slot。
- 检查 catalyst。
- 检查 chance tooltip。

## 自动化检查建议

只读检查：

- 搜索旧 `com.lowdragmc.lowdraglib.gui` import 数量。
- 搜索旧 `ModularUI` 引用。
- 搜索旧 `WidgetGroup` 引用。
- 搜索旧 `IngredientIO.RENDER_ONLY`。
- 搜索 LDLib2 RPC 注解或 RPC 调用，必须为 0。

构建检查：

- Gradle 编译。
- 数据生成如果涉及 UI 资源，单独运行。
- 不需要为纯迁移文档运行 Gradle。

## 风险清单

### 高风险

- `SlotWidget` 和 `TankWidget` 行为回归。
- Cover holder 失效写入错误 cover。
- Configurator action 误用 LDLib2 RPC。
- Recipe slot role 映射错误。
- `.rtui` 自定义 UI 不兼容。

### 中风险

- 绝对布局偏移。
- tooltip 内容丢失。
- Scene 预览相机参数不一致。
- Item UI holder 校验不足。

### 低风险

- 静态贴图路径调整。
- tab hover/pressed 样式偏差。
- 文本颜色和 spacing 差异。

## 完成标准

迁移完成时应满足：

- `compileOnly(forge.ldlib)` 可以移除或不再被源码需要。
- 旧 `lowdraglib.gui` 引用清零或仅剩明确兼容层。
- 机器、Cover、Item、Recipe UI 都基于 LDLib2 UI。
- 客户端到服务端交互全部走 GT sync。
- 动作型交互全部走 GT action。
- 不存在 LDLib2 RPC。
- 主要 UI 手动验证通过。
