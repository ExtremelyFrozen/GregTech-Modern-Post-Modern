# 00. 范围与决策

## 目标

将 `GregTech-Modern` 中仍依赖旧 LDLib UI API 的界面迁移到 LDLib2 UI。迁移范围包括：

- 机器 UI 打开链路。
- Cover UI 打开链路。
- Item/Tool UI 打开链路。
- GTM 自有控件层。
- Fancy Machine UI 框架。
- Recipe UI 与 XEI/JEI/EMI/REI 集成。
- UI 相关渲染预览，例如机器 `Scene`。
- UI 交互所需的 GT 同步能力扩展。

## 已确认决策

### 只使用 GT 同步系统

所有 UI 到服务端的交互只能通过 GTM 自己的 `api.sync_system` 完成。

允许使用：

- `@SaveField`
- `@SyncToClient`
- `@SyncToServer`
- `@SyncBoth`
- `@RerenderOnChanged`
- `@ClientFieldChangeListener`
- `SyncDataHolder`
- `ManagedSyncBlockEntity`
- `DataComponentMap`
- GTM 自有 network packet

不允许使用：

- LDLib2 `@RPCMethod`
- LDLib2 RPC channel
- 为 UI 单独引入另一套通用 RPC 框架

### LDLib2 RPC 需求转为 GT sync action

LDLib2 UI 中常见的按钮点击、服务端确认、一次性命令等 RPC-like 语义，迁移时不直接映射到 LDLib2 RPC。

统一策略：

- 字段状态变化使用 `@SyncToServer` 或 `@SyncBoth`。
- 一次性动作扩展 GT sync action 能力。
- action 仍使用 GTM 的 holder 校验、packet 校验和 `DataComponentMap` 序列化。

### Cover UI 使用 `UICoverHolder`

Cover UI 使用方案 3：

- 新增 GTM `UICoverHolder` 包装对象。
- `UICoverHolder` 负责持有和解析 `BlockPos + Direction`。
- LDLib2 menu 面向 `UICoverHolder`。
- 业务逻辑继续面向 `CoverBehavior`。
- 字段同步和 action 同步由 holder 转发到 cover。

这能把 LDLib2 menu holder 需求与 GTM cover 业务模型隔离开，避免强行把 cover 当作普通 block entity。

## 迁移原则

### 先迁抽象层，再迁调用点

GTM 已经有较完整 UI 抽象：

- `api.gui.factory`
- `api.gui.widget`
- `api.gui.fancy`
- `api.recipe.ui`
- `api.machine.feature`
- `api.cover`
- `api.item.component`

迁移应先让这些抽象层提供 LDLib2 版本，再批量处理 `common.machine`、`common.cover`、`common.item`、`integration.ae2` 等调用点。

### 保留业务语义

迁移 UI 不应改变：

- 机器能否打开 UI 的条件。
- 玩家背包绑定语义。
- slot 输入输出限制。
- tank 点击装桶/倒桶语义。
- cover 安装、拆除、替换语义。
- recipe slot 的 input/output/catalyst 语义。
- 同步字段含义。

### 尽量保留 GTM 自有 API

第一阶段不要求所有调用点直接使用 LDLib2 风格 API。可以保留 GTM 自有 facade，例如：

- `createUIWidget()`
- `attachConfigurators(...)`
- `attachTooltips(...)`
- `bindPlayerInventory(...)`
- `setLocationInfo(...)`
- recipe slot id 规则

facade 内部再转换为 LDLib2 `UIElement`、layout、style 和 event。

## 禁止项

- 不使用 LDLib2 RPC。
- 不把 UI 迁移和机器业务逻辑重构混在一起。
- 不为了迁移 UI 修改 recipe 执行语义。
- 不在业务类中长期保留旧 LDLib UI 与 LDLib2 UI 两套并行实现。
- 不通过反射绕过 holder、同步或 menu 访问限制。
- 不创建无实际行为的占位实现。
- 不吞异常；holder 无效、action 未知、payload 不匹配时需要记录日志并拒绝执行。

## 仍需后续确认的事项

- GT sync action 是复用现有 sync packet，还是新增独立 action packet。
- UI Editor 是否保留。
- `.rtui` 是否转换。
- Recipe Viewer 是否一次性恢复 JEI/EMI/REI，还是按当前运行依赖逐个恢复。
- Cover UI 关闭时 `onUIClosed()` 是否必须发送服务端通知。
