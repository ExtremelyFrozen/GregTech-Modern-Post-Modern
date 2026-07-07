# 01. 当前状态盘点

## 依赖状态

当前 `dependencies.gradle` 中存在两个关键信号：

- `compileOnly(forge.ldlib)`
- `jarJar(api(forge.ldlib2.get()))`

这表示源码仍编译依赖旧 LDLib API，但运行产物已包含 LDLib2。UI 迁移的第一目标不是添加 LDLib2 依赖，而是移除旧 `lowdraglib.gui` 使用面。

## 自动清单入口

用于生成并冻结迁移清单的可运行命令：

```powershell
$env:GRADLE_USER_HOME='E:\.gradle'; .\gradlew.bat reportLdlib2MigrationInventory --no-daemon
```

输出路径：

`build/reports/ldlib2-migration/inventory.txt`

当前本地运行结果：

- `2010 matches`
- `legacy-package=295`
- `legacy-ui-model=1714`
- `legacy-xei-role=1`

该报告任务是非失败型 baseline inventory，只负责生成可审阅清单；真正失败型 gate 仍是 `verifyLdlib2MigrationGuardrails`，以及已接入 `check` 的 migrated-surfaces/Gradle-home 护栏。

## 旧 UI 打开入口

### `MachineUIFactory`

路径：

`src/main/java/com/gregtechceu/gtceu/api/gui/factory/MachineUIFactory.java`

当前职责：

- 继承旧 `UIFactory<MetaMachine>`。
- `createUITemplate(...)` 调用 `IUIMachine.createUI(...)`。
- 通过 `BlockPos` 在客户端读回 `MetaMachine`。
- 写入打开 UI 所需的 holder sync data。

迁移影响：

- 旧 `UIFactory` 和旧 `ModularUI` 必须移除。
- 机器 holder 定位仍可以保留 `BlockPos`。
- 需要建立 LDLib2 menu 打开链路。

### `IUIMachine`

路径：

`src/main/java/com/gregtechceu/gtceu/api/machine/feature/IUIMachine.java`

当前职责：

- 继承旧 `IUIHolder`。
- `tryToOpenUI(...)` 在服务端调用 `MachineUIFactory.INSTANCE.openUI(...)`。
- `markAsDirty()` 调用 `self().sendServerSyncChanges()`。

迁移影响：

- 旧 `IUIHolder` 必须移除。
- `tryToOpenUI(...)` 作为业务入口应保留。
- `markAsDirty()` 的语义需要继续接到 GT sync。

### `CoverUIFactory`

路径：

`src/main/java/com/gregtechceu/gtceu/api/gui/factory/CoverUIFactory.java`

当前职责：

- 继承旧 `UIFactory<CoverBehavior>`。
- 通过 `BlockPos + Direction` 定位 cover。
- `createUITemplate(...)` 调用 `IUICover.createUI(...)`。

迁移影响：

- 旧 factory 必须替换。
- Cover 不应直接假装是 block entity holder。
- 已确认使用 `UICoverHolder` 包装对象。

### `IUICover`

路径：

`src/main/java/com/gregtechceu/gtceu/api/cover/IUICover.java`

当前职责：

- 继承旧 `IUIHolder`。
- 创建旧 `ModularUI`。
- 自动添加玩家背包。
- 注册 `onUIClosed()`。
- `markAsDirty()` 发送 `CPacketCoverSyncToServer`。

迁移影响：

- `createUI(...)` 需要改为 LDLib2 UI tree。
- `createUIWidget()` 可以保留为 GTM 自有 UI 入口。
- `onUIClosed()` 是否保留服务端通知需要后续确认。
- `markAsDirty()` 不使用 LDLib2 RPC，继续转 GT sync。

### `IItemUIFactory`

路径：

`src/main/java/com/gregtechceu/gtceu/api/item/component/IItemUIFactory.java`

当前职责：

- 使用旧 `HeldItemUIFactory`。
- 打开手持物品 UI。
- 创建旧 `ModularUI`。

迁移影响：

- 需要迁到 LDLib2 `HeldItemUIMenuType` 或 GTM item holder bridge。
- `InteractionHand` 必须继续作为定位信息。

### `GTUIEditorFactory`

路径：

`src/main/java/com/gregtechceu/gtceu/api/gui/factory/GTUIEditorFactory.java`

当前职责：

- 旧 `UIFactory`。
- 打开 `GTUIEditor`。
- 使用 `LDLib.location(...)`。

迁移影响：

- UI Editor 是后期阶段。
- 不应阻塞普通机器 UI、Cover UI、Recipe UI 迁移。

## 核心控件层

主要路径：

`src/main/java/com/gregtechceu/gtceu/api/gui/widget`

重要类：

- `SlotWidget`
- `TankWidget`
- `ToggleButtonWidget`
- `PredicatedButtonWidget`
- `PredicatedImageWidget`
- `EnumSelectorWidget`
- `NumberInputWidget`
- `IntInputWidget`
- `LongInputWidget`
- `ConfirmTextInputWidget`
- `ExtendedProgressWidget`
- `DualProgressWidget`
- `PhantomSlotWidget`
- `PhantomFluidWidget`
- `GhostCircuitSlotWidget`
- `PatternPreviewWidget`
- `ProspectingMapWidget`

迁移判断：

- `SlotWidget` 和 `TankWidget` 是最关键的桥接点。
- 大量业务代码依赖 GTM 自有控件名，不应在第一阶段全部替换调用点。
- 控件内部应迁到 LDLib2 `UIElement` 和具体元素。

## Fancy UI 框架

主要路径：

`src/main/java/com/gregtechceu/gtceu/api/gui/fancy`

重要类：

- `FancyMachineUIWidget`
- `TabsWidget`
- `VerticalTabsWidget`
- `PageSwitcher`
- `TitleBarWidget`
- `ConfiguratorPanel`
- `TooltipsPanel`
- `IFancyConfigurator`
- `IFancyConfiguratorButton`
- `IFancyUIProvider`

相关机器入口：

`src/main/java/com/gregtechceu/gtceu/api/machine/feature/IFancyUIMachine.java`

当前特点：

- `IFancyUIMachine.createUI(...)` 直接创建旧 `ModularUI`。
- `createUIWidget()` 默认构建旧 `WidgetGroup`。
- 默认机器预览使用旧 `SceneWidget`。
- tabs、configurator、tooltip 都依赖旧 `Widget` 模型。

迁移判断：

- Fancy UI 是普通机器、多方块机器和 configurator 的核心复用层。
- 迁移优先级高于单个机器界面。

## Recipe UI 与 XEI

主要入口：

`src/main/java/com/gregtechceu/gtceu/api/recipe/ui/GTRecipeTypeUI.java`

当前特点：

- 自动布局返回旧 `WidgetGroup`。
- 使用旧 `ProgressWidget`、`ButtonWidget`、`SlotWidget`、`TankWidget`。
- 通过 widget id 匹配 recipe capability。
- XEI 展示复用旧控件。

相关路径：

- `src/main/java/com/gregtechceu/gtceu/integration/xei/widgets`
- `src/main/java/com/gregtechceu/gtceu/integration/jei/recipe`
- `src/main/java/com/gregtechceu/gtceu/integration/emi/recipe`

迁移判断：

- Recipe UI 应在基础控件和 Fancy UI 稳定后迁移。
- 旧 `IngredientIO.RENDER_ONLY` 需要改成 LDLib2 XEI 的明确 slot 语义。

## GT 同步系统现状

主要路径：

`src/main/java/com/gregtechceu/gtceu/api/sync_system`

关键能力：

- `SyncDataHolder` 持有同步元数据和 dirty 状态。
- `scanAndMarkChanges(...)` 生成 client sync changes。
- `collectServerNetworkChanges(...)` 生成客户端到服务端的字段更新。
- `applyServerNetworkUpdate(...)` 应用客户端到服务端更新。
- `applyClientNetworkUpdate(...)` 应用服务端到客户端更新。
- `@RerenderOnChanged` 触发客户端 render update。
- `@ClientFieldChangeListener` 触发客户端字段变化回调。

网络包：

- `CPacketMachineSyncToServer`
- `SPacketMachineSyncToClient`
- `CPacketCoverSyncToServer`

迁移判断：

- 现有字段同步能力可以覆盖大多数控件状态变化。
- 一次性按钮动作需要新增 GT sync action 能力。
- Cover 已有字段回传 packet，但 `UICoverHolder` 需要统一 holder 校验和 action 转发。
