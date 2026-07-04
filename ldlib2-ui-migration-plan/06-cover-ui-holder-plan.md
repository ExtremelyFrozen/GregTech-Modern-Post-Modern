# 06. Cover UI Holder 方案

## 已选方案

Cover UI 使用方案 3：新增 GTM `UICoverHolder` 包装对象。

该方案的核心目标是隔离两件事：

- LDLib2 menu 需要一个稳定 holder。
- GTM cover 业务对象仍然是 `CoverBehavior`，并通过 `BlockPos + Direction` 定位。

`UICoverHolder` 不替代 `CoverBehavior`，只负责 UI 生命周期、holder 解析、同步转发和失效校验。

## 当前 Cover UI 模型

当前 `IUICover`：

- 继承旧 `IUIHolder`。
- `createUI(...)` 返回旧 `ModularUI`。
- 自动绑定 player inventory。
- 提供 `onUIClosed()`。
- `markAsDirty()` 收集 cover sync changes。
- 使用 `CPacketCoverSyncToServer(pos, side, changes)` 回传。

当前 `CPacketCoverSyncToServer`：

- payload 包含 `BlockPos`、`Direction`、`DataComponentMap`。
- 服务端通过 `GTCapabilityHelper.getCoverable(level, pos, side)` 找 coverable。
- 取 `coverable.getCoverAtSide(side)`。
- 调用 `cover.getSyncDataHolder().applyServerNetworkUpdate(...)`。

## `UICoverHolder` 职责

### 定位数据

`UICoverHolder` 应持有：

- `BlockPos`
- `Direction`
- 可选 cover definition id
- 可选 UI open sequence

`BlockPos + Direction` 是实际定位信息。cover definition id 用于校验打开时的 cover 和当前 cover 是否一致。

### 服务端职责

服务端 holder 负责：

- 打开 UI 前校验 cover 存在。
- 校验玩家是否仍可访问 cover。
- 构建 root UIElement。
- 接收字段同步。
- 接收 action 同步。
- 在 close 时触发 `onUIClosed()`，如果最终要求保留服务端 close 通知。

### 客户端职责

客户端 holder 负责：

- 根据 `BlockPos + Direction` 查找当前 cover。
- 判断 cover 是否仍有效。
- 为 UI element 提供 cover 当前状态。
- holder 失效时关闭 UI 或展示失效状态。

### 与 `CoverBehavior` 的关系

`UICoverHolder` 不保存业务状态。业务状态仍在 `CoverBehavior` 及其 sync fields 中。

holder 提供方法语义：

- resolve cover。
- check valid。
- collect server changes。
- apply client changes。
- send action。

最终实现命名可以调整，但职责必须保留。

## 打开协议

### 服务端打开

流程：

1. cover 请求打开 UI。
2. 取得 `coverHolder.getBlockPos()` 和 `attachedSide`。
3. 构造 `UICoverHolder`。
4. 校验 cover 当前存在。
5. 使用 LDLib2 menu 打开 UI。
6. 打开数据写入 `BlockPos + Direction`，可选写入 cover definition id。

### 客户端创建

流程：

1. 客户端 menu 读取 `BlockPos + Direction`。
2. 创建客户端 `UICoverHolder`。
3. 解析当前 cover。
4. 如果 cover 不存在，UI 显示失效状态或立即关闭。
5. 如果 cover 存在，创建 cover UIElement。

## 字段同步协议

### 客户端到服务端

字段型交互：

1. UI 控件修改 cover 字段。
2. 字段标注 `@SyncToServer` 或 `@SyncBoth`。
3. `UICoverHolder` 收集 `cover.getSyncDataHolder().collectServerNetworkChanges(...)`。
4. 发送 cover field sync packet。
5. 服务端解析 `UICoverHolder`。
6. 服务端再次解析 cover。
7. 服务端应用 `applyServerNetworkUpdate(...)`。

### 服务端到客户端

服务端 cover 状态变化：

1. cover 字段标注 `@SyncToClient` 或 `@SyncBoth`。
2. cover 所在 machine/block entity tick 时扫描变化。
3. 通过 cover 所属机器或 cover container 的同步路径发送到客户端。
4. 客户端 `CoverBehaviorCodec` 或 holder sync path 应用到 cover。

需要注意：

- 当前只有 `CPacketCoverSyncToServer`，没有独立 `SPacketCoverSyncToClient`。
- 服务端到客户端 cover 同步通常依赖 cover 所在 managed object 的 contextual codec。
- 迁移时必须确认 cover 字段变化能继续抵达客户端 UI。

## 动作同步协议

动作型交互不使用 LDLib2 RPC。

建议新增：

- `CPacketCoverActionToServer`
- `SyncActionData`
- cover action handler registry 或注解

payload：

- `BlockPos`
- `Direction`
- `SyncActionData`

服务端处理：

1. 校验玩家。
2. 校验 chunk loaded。
3. 用 `BlockPos + Direction` 解析 coverable。
4. 解析 cover。
5. 校验 cover definition id。
6. 查找 action handler。
7. 校验 payload。
8. 执行动作。
9. 标记 cover 或所在 block entity changed。

## 生命周期

### holder 有效

有效条件：

- level 存在。
- chunk loaded。
- block entity 或 coverable 存在。
- `getCoverAtSide(side)` 不为空。
- cover definition 与打开时一致，除非允许 UI 自动切换到新 cover。

### holder 失效

失效场景：

- 方块被移除。
- cover 被拆除。
- cover 被替换。
- side 不再有 cover。
- 玩家距离过远或权限失效。

建议行为：

- 客户端发现失效时关闭 UI。
- 服务端收到失效 holder 的 sync/action 时拒绝执行并记录 debug 或 warn 日志。

### close listener

旧 `IUICover.onUIClosed()` 存在。迁移时有两个选项：

- 只在客户端关闭本地 UI，不通知服务端。
- 保留服务端 close 通知，由 `UICoverHolder` 在 menu close 时调用 cover 的 `onUIClosed()`。

当前未最终确认。若任何 cover 依赖 close 保存状态，则必须保留服务端 close 通知。

## 权限校验

Cover UI action 至少应校验：

- 玩家仍能访问所在机器。
- 玩家与 block 距离合理。
- cover 当前存在。
- action 对当前 cover type 合法。

如果已有机器 UI 权限逻辑，应复用，不要为 cover 单独复制一套不一致逻辑。

## 迁移步骤

1. 新增 `UICoverHolder` 包装对象。
2. 新增 cover UI bridge。
3. 修改 Cover UI 打开链路，让 LDLib2 menu 面向 `UICoverHolder`。
4. `IUICover` 移除旧 `IUIHolder` 依赖。
5. `IUICover.createUIWidget()` 改为返回 LDLib2 element 或 GTM wrapper element。
6. 字段型交互继续通过 cover sync data 回传。
7. 动作型交互通过 GT sync action。
8. 处理 close listener。
9. 验证 cover 拆除、替换、chunk unload、玩家远离等失效路径。

## 验收清单

- Cover UI 可以打开。
- Cover UI root 由 `UICoverHolder` 提供 holder。
- Cover 被拆除后 UI 不再能修改旧 cover。
- Cover 被替换后旧 UI 不会把数据写到新 cover，除非明确允许。
- 字段同步只走 GT sync。
- 动作同步只走 GT action。
- 不存在 LDLib2 RPC。
- 失效 holder 有日志，不静默执行。
