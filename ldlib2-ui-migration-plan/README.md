# GregTech Modern LDLib2 UI 迁移计划

本目录是 `GregTech-Modern` 的 LDLib2 UI 迁移计划文档。文档只描述计划、边界、风险和验收标准，不修改源码。

## 已确认决策

- UI 迁移目标是从旧 LDLib `lowdraglib.gui` 迁到 LDLib2 UI。
- 同步只能使用 GTM 自己的 `api.sync_system`。
- 迁移中需要 RPC 语义的地方，不使用 LDLib2 RPC，而是给 GTM 同步系统补充对应 action 能力。
- Cover UI 使用方案 3：新增 GTM `UICoverHolder` 包装对象，隔离 LDLib2 menu holder 与 `CoverBehavior` 业务模型。
- 文档拆分为多个专题文件，后续按阶段维护。

## 文档索引

- [00-scope-and-decisions.md](00-scope-and-decisions.md)
  - 迁移边界、已确认决策、禁止项和未决项。
- [01-current-state-inventory.md](01-current-state-inventory.md)
  - 当前旧 LDLib UI 入口、控件层、Recipe/XEI、GT sync 现状。
- [02-ui-entry-migration.md](02-ui-entry-migration.md)
  - 机器 UI、Item UI、Cover UI、UI Editor 打开链路迁移计划。
- [03-widget-and-layout-migration.md](03-widget-and-layout-migration.md)
  - `Widget`/`WidgetGroup` 到 `UIElement`，基础控件和布局迁移策略。
- [04-fancy-machine-ui-migration.md](04-fancy-machine-ui-migration.md)
  - `FancyMachineUIWidget`、tabs、configurator、tooltip、Scene 预览迁移。
- [05-gt-sync-only-strategy.md](05-gt-sync-only-strategy.md)
  - 只使用 GT sync 的字段同步、动作同步、错误处理和验证计划。
- [06-cover-ui-holder-plan.md](06-cover-ui-holder-plan.md)
  - `UICoverHolder` 方案的协议、生命周期、同步回传和验收标准。
- [07-recipe-xei-migration.md](07-recipe-xei-migration.md)
  - `GTRecipeTypeUI`、recipe slot、JEI/EMI/REI/XEI 迁移计划。
- [08-rollout-and-validation.md](08-rollout-and-validation.md)
  - 阶段顺序、验收矩阵、回归测试和风险清单。

## 推荐阅读顺序

1. 先读 [00-scope-and-decisions.md](00-scope-and-decisions.md)，确认迁移边界。
2. 再读 [01-current-state-inventory.md](01-current-state-inventory.md)，理解当前依赖面。
3. 实施入口迁移前读 [02-ui-entry-migration.md](02-ui-entry-migration.md)。
4. 实施控件迁移前读 [03-widget-and-layout-migration.md](03-widget-and-layout-migration.md)。
5. 处理机器主 UI 时读 [04-fancy-machine-ui-migration.md](04-fancy-machine-ui-migration.md)。
6. 任何涉及交互回传、按钮命令、客户端输入的改动，都必须先读 [05-gt-sync-only-strategy.md](05-gt-sync-only-strategy.md)。
7. Cover UI 迁移必须按 [06-cover-ui-holder-plan.md](06-cover-ui-holder-plan.md) 执行。

## 非目标

- 不在本目录内提供最终源码实现。
- 不修改 `src/`、`dependencies.gradle`、README 或现有项目文档。
- 不引入 LDLib2 RPC。
- 不在 UI 迁移中重构机器业务逻辑、配方逻辑、材料系统或能源/物品/流体传输逻辑。
