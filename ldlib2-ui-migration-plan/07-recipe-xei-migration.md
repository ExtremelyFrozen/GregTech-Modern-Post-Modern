# 07. Recipe UI 与 XEI 迁移

## 目标

迁移 `GTRecipeTypeUI` 和 Recipe Viewer 相关 UI，使 recipe 展示、slot 绑定、fluid 展示、progress、chance tooltip、phantom/circuit slot 在 LDLib2 UI 和 XEI 集成中保持一致。

## 当前模型

关键入口：

`src/main/java/com/gregtechceu/gtceu/api/recipe/ui/GTRecipeTypeUI.java`

当前职责：

- 读取 `.rtui` 自定义 recipe UI。
- 自动生成 input/output slot group。
- 创建 progress widget。
- 使用 widget id 绑定 recipe capability。
- 在非 JEI 展示时给 progress 添加打开 recipe viewer 的按钮。
- 通过 `RecipeCapability.applyWidgetInfo(...)` 注入 slot 内容。

当前控件：

- `WidgetGroup`
- `ProgressWidget`
- `ButtonWidget`
- `SlotWidget`
- `TankWidget`
- `DualProgressWidget`

## 迁移目标模型

### Recipe UI tree

迁移后 `GTRecipeTypeUI` 应生成 LDLib2 UI tree。

建议保留：

- recipe holder 数据结构。
- recipe capability 排序。
- input/output 分组。
- slot id 命名规则。
- progress id `progress`。
- custom UI fallback 机制。

### Capability 到 UI 的绑定

现有绑定依赖：

- widget id。
- widget class。
- slot index。
- IO direction。

迁移后仍建议保留 id 规则，但 widget class 改为 LDLib2 element class 或 GTM wrapper element class。

## Slot 语义迁移

旧 `IngredientIO.RENDER_ONLY` 不再作为迁移目标。

新语义必须显式：

| 旧用途 | LDLib2 XEI 语义 |
| --- | --- |
| recipe input | `INPUT` |
| recipe output | `OUTPUT` |
| programmed circuit | `CATALYST` 或 input，按 recipe 语义决定 |
| tool/not consumed slot | `CATALYST` |
| purely visual slot | `NONE` |
| old render-only display | `NONE`，如果参与 recipe viewer 则拆为具体语义 slot |

如果一个旧控件同时承担展示和 recipe ingredient，需要拆分：

- 一个可见 slot 负责 UI 展示。
- 一个 XEI metadata slot 负责 recipe viewer 语义。

## Item slot 迁移

Recipe item slot 需要支持：

- 单个 `ItemStack`。
- tag ingredient。
- holder set ingredient。
- cycle item entry。
- chance。
- tooltip。
- clickable ingredient。
- phantom recipe display。

迁移到 LDLib2 `ItemSlot` 时，需要统一设置：

- recipe slot role。
- phantom 状态。
- recipe ingredient supplier。
- tooltip supplier。
- chance metadata。

## Fluid slot 迁移

Recipe fluid slot 需要支持：

- `FluidStack`。
- tag fluid。
- holder set fluid。
- cycle fluid entry。
- amount。
- chance。
- tooltip。
- clickable ingredient。

迁移到 LDLib2 `FluidSlot` 时，需要统一设置：

- recipe slot role。
- phantom 状态。
- recipe fluid ingredient supplier。
- amount display。
- chance metadata。

## Progress 迁移

Recipe progress 有两类：

- XEI 固定展示 progress。
- 机器运行时 progress。

迁移策略：

- 保留 `ProgressWidget.JEIProgress` 等价语义。
- `progress` id 继续可被绑定。
- `DualProgressWidget` 需要明确是否作为 GTM 自定义 LDLib2 element。
- Steam progress texture 和 fill direction 继续支持。

## Recipe Viewer 按钮

旧逻辑：

- 非 JEI 展示时，在 progress widget 上覆盖透明 button。
- 点击后打开 EMI 或 JEI recipe category。

迁移策略：

- 这是客户端本地动作，不需要 GT sync。
- 可以使用 LDLib2 `Button`。
- 不涉及服务端 RPC。
- 需要同时保持 EMI 和 JEI 的 runtime 检测。

## 自定义 `.rtui`

`GTRecipeTypeUI` 当前会加载 `ui/recipe_type/*.rtui`。

迁移选项：

- 暂不支持 `.rtui`，全部 fallback 到自动布局。
- 写一次性 `.rtui` 转 LDLib2 UI/LSS 的转换器。
- 重建编辑器和运行期 loader。

推荐：

- 第一轮先 fallback 到自动布局。
- 单独统计 `.rtui` 数量和使用场景。
- 第二轮再决定转换或重建。

## JEI/EMI/REI 集成

目标：

- 统一进入 LDLib2 `integration.xei` 风格。
- 避免旧 LDLib JEI wrapper。
- 保持 GTM 现有 `integration.jei` 和 `integration.emi` 的 category 语义。

迁移步骤：

1. 抽出 GTM recipe UI element builder。
2. JEI category 使用同一个 builder。
3. EMI category 使用同一个 builder。
4. REI 如需要支持，也接入同一 builder。
5. 每个 slot 显式声明 XEI role。

## 验收清单

- 自动布局 recipe UI 正常显示。
- input/output slot 数量和位置正确。
- progress texture 正确。
- Steam recipe UI 正确。
- item/fluid chance tooltip 正确。
- programmed circuit 显示正确。
- JEI/EMI 中点击 ingredient 正确。
- 旧 `IngredientIO.RENDER_ONLY` 不再作为迁移后语义。
- Recipe Viewer 按钮是客户端本地行为，不走 GT sync。
