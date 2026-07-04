# 05. 只使用 GT 同步系统的策略

## 硬性约束

UI 迁移过程中，客户端到服务端、服务端到客户端、UI action、field update 都只能使用 GTM 自己的同步系统。

禁止：

- LDLib2 `@RPCMethod`。
- LDLib2 RPC packet。
- 绕过 GT holder 校验直接调用服务端逻辑。

允许：

- 现有 `SyncDataHolder` 字段同步。
- 现有 machine/cover packet。
- 在 `api.sync_system` 下新增 GT action 能力。

## 现有字段同步模型

### 服务端到客户端

典型流程：

1. 字段标注 `@SyncToClient` 或 `@SyncBoth`。
2. 字段变化后标记 dirty，或由 contextual codec 检测变化。
3. `SyncDataHolder.scanAndMarkChanges(...)` 生成变化。
4. `ManagedSyncBlockEntity.serverTick()` 发送 `SPacketMachineSyncToClient`。
5. 客户端 `applyClientNetworkUpdate(...)` 应用字段。
6. 如果字段有 `@ClientFieldChangeListener`，调用监听。
7. 如果字段有 `@RerenderOnChanged`，触发重渲染。

### 客户端到服务端

机器字段流程：

1. 字段标注 `@SyncToServer` 或 `@SyncBoth`。
2. 客户端 UI 修改字段。
3. 调用 `sendServerSyncChanges()`。
4. `SyncDataHolder.collectServerNetworkChanges(...)` 收集变化。
5. `CPacketMachineSyncToServer` 发送到服务端。
6. 服务端 `applyServerNetworkUpdate(...)` 应用字段。

Cover 字段流程：

1. cover 字段标注 `@SyncToServer` 或 `@SyncBoth`。
2. 客户端 UI 修改 cover 字段。
3. `UICoverHolder` 或 cover bridge 收集变化。
4. 使用 cover sync packet 或 holder action packet 发送。
5. 服务端解析 holder，再应用到 `CoverBehavior`。

## 交互分类

### 字段型交互

字段型交互表示 UI 修改一个持久或同步字段。

示例：

- 开关。
- enum 模式。
- 数字配置。
- 文本配置。
- filter 参数。
- cover mode。

处理方式：

- 优先使用 `@SyncToServer`。
- 如果客户端也需要立即更新展示，使用 `@SyncBoth` 或本地 optimistic UI 状态。
- 服务端必须再次校验。

### 动作型交互

动作型交互表示一次性命令，不适合表达成字段。

示例：

- 清空 filter。
- 重置配置。
- 中键清空。
- 滚轮执行一步操作。
- 请求服务器执行某个按钮动作。
- 请求服务器刷新某个只在服务端可靠的数据。

处理方式：

- 新增 GT sync action 能力。
- action 使用 GT holder 校验。
- action payload 使用 GT sync 序列化体系。
- action handler 不通过 LDLib2 RPC 暴露。

## GT sync action 设计建议

### `SyncActionData`

建议数据结构：

- `actionId`
  - 字符串或注册 id。
  - 表示要执行的动作。
- `sequence`
  - 客户端递增序号。
  - 用于排查重复提交和日志追踪。
- `payload`
  - `DataComponentMap` 或 `SyncFieldData`。
  - 不新建完全独立序列化体系。

### action handler

建议新增注解或注册表：

- `@SyncActionHandler`
- 或显式 `SyncActionRegistry`

要求：

- handler 只在服务端执行。
- handler 所属对象必须来自有效 holder。
- handler 必须校验玩家权限。
- handler 必须校验 payload。
- 未知 action 记录日志并拒绝执行。
- handler 不应吞异常。

### packet 选择

有两个可选实现方向。

#### 方案 1：独立 action packet

新增：

- `CPacketMachineActionToServer`
- `CPacketCoverActionToServer`
- 可选 `SPacketUIActionResultToClient`

优点：

- 字段同步和动作同步分离。
- 日志和调试清晰。
- 不污染 `SyncFieldData`。

缺点：

- 需要新增 packet 注册和测试。

#### 方案 2：复用 sync packet，payload 中包含 action component

做法：

- 在 `DataComponentMap` 中加入 action component。
- 服务端 packet 根据 component 判断是 field update 还是 action update。

优点：

- packet 数量少。
- 可以复用已有发送路径。

缺点：

- 字段同步和动作同步边界不清。
- 更容易误把 action 当字段保存或缓存。

推荐：

- 机器和 Cover 都使用独立 action packet。
- 字段同步继续使用现有 field packet。

## UI 事件到 GT sync 的映射

| UI 事件 | 处理方式 |
| --- | --- |
| hover | 本地，不同步 |
| tooltip 展示 | 本地，不同步 |
| tab 切换 | 本地，不同步 |
| button toggle | 字段同步 |
| button command | GT action |
| selector change | 字段同步 |
| text input typing | 本地，不同步 |
| text input commit | 字段同步或 GT action |
| slot insert/extract | 现有 container/handler 机制或字段同步 |
| phantom slot set | 字段同步 |
| tank bucket click | 现有交互路径或 GT action |
| mouse wheel adjust | 字段同步或 GT action |
| middle click reset | GT action |

## 错误处理

必须 fail fast 并记录日志的场景：

- holder 不存在。
- holder 类型不匹配。
- cover side 没有 cover。
- action id 未注册。
- payload 缺字段。
- payload 类型不匹配。
- 玩家没有权限。
- 客户端发送服务端不允许修改的字段。

不应：

- 空 `catch`。
- 静默忽略未知 action。
- 使用默认值继续执行危险操作。
- 用 LDLib2 RPC 兜底。

## 验收清单

- 所有 UI 到服务端交互都能定位到 GT sync field 或 GT sync action。
- 搜索不到 LDLib2 RPC 注解或调用。
- 字段型交互不走 action。
- 动作型交互不伪装成持久字段。
- 服务端 packet 处理包含 holder 校验和玩家权限校验。
- 错误路径有日志。
