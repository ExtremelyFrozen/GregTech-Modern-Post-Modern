# 02. UI 打开链路迁移

## 目标

将机器 UI、Cover UI、Item/Tool UI 和 UI Editor 从旧 `UIFactory` / `IUIHolder` / `ModularUI` 打开链路迁移到 LDLib2 menu 体系，同时保留 GTM 业务入口和 GT sync 语义。

## 统一设计

迁移后建议形成 GTM 自有的 UI bridge 层，避免业务类直接依赖 LDLib2 menu 细节。

建议 bridge 类型：

- `MachineUIBridge`
- `CoverUIBridge`
- `ItemUIBridge`
- `GTUIElementFactory`

职责：

- 打开 UI。
- 写入 holder 定位数据。
- 在客户端解析 holder。
- 构建 LDLib2 `UIElement` root。
- 对接 GT sync。

这些名称是计划名，不要求最终实现完全一致。

## 机器 UI 打开链路

### 当前链路

1. 玩家右键机器。
2. `IUIMachine.tryToOpenUI(...)` 判断 `shouldOpenUI(...)`。
3. 服务端调用 `MachineUIFactory.INSTANCE.openUI(...)`。
4. 旧 `UIFactory` 写入 `BlockPos`。
5. 客户端 `readHolderFromSyncData(...)` 用 `BlockPos` 找回 `MetaMachine`。
6. `createUITemplate(...)` 调用 `IUIMachine.createUI(...)`。

### 目标链路

1. 玩家右键机器。
2. `IUIMachine.tryToOpenUI(...)` 保留。
3. 服务端调用 GTM machine UI bridge。
4. bridge 使用 LDLib2 block menu 打开 UI。
5. 打开数据继续包含 `BlockPos`。
6. 客户端解析 `MetaMachine`。
7. 调用 GTM LDLib2 UI builder 创建 root `UIElement`。

### 改造点

- `IUIMachine` 不再继承旧 `IUIHolder`。
- `MachineUIFactory` 不再继承旧 `UIFactory`。
- `createUI(Player)` 的返回类型需要改成 GTM 自有类型或 LDLib2 root element。
- `markAsDirty()` 继续表示“客户端 UI 有字段更新需要发送给服务端”，最终调用 GT sync。

### 风险

- 旧 `ModularUI` 自动处理的 slot/container 行为，需要在 LDLib2 menu 中逐项恢复。
- 如果某些机器自定义 `createUI(...)` 返回旧 `ModularUI`，需要迁移为 root element builder。
- `MetaMachine` 生命周期和 block entity 生命周期需要继续一致。

## Cover UI 打开链路

Cover UI 使用 `UICoverHolder`，详细协议见 [06-cover-ui-holder-plan.md](06-cover-ui-holder-plan.md)。

### 当前链路

1. 某个 cover 触发打开 UI。
2. 旧 `CoverUIFactory` 写入 `BlockPos + Direction`。
3. 客户端用 `GTCapabilityHelper.getCoverable(...)` 找 cover。
4. `IUICover.createUI(...)` 创建旧 `ModularUI`。
5. `IUICover.markAsDirty()` 用 `CPacketCoverSyncToServer` 回传字段。

### 目标链路

1. cover 触发打开 UI。
2. 服务端创建 `UICoverHolder`，内部保存 `BlockPos + Direction`。
3. LDLib2 menu 面向 `UICoverHolder` 打开。
4. 客户端 `UICoverHolder` 解析当前 cover。
5. root UIElement 由 `IUICover.createUIWidget()` 或新 builder 创建。
6. 字段回传和 action 回传都通过 `UICoverHolder` 转发到 `CoverBehavior`。

### 保留项

- `BlockPos + Direction` 作为实际 cover 定位数据。
- `CoverBehavior` 业务模型。
- `CPacketCoverSyncToServer` 的字段同步语义，除非后续为了 holder 统一而封装。

### 变化项

- LDLib2 menu 不直接持有 `CoverBehavior`。
- `IUICover` 不再继承旧 `IUIHolder`。
- `onUIClosed()` 需要由 `UICoverHolder` 或 Cover UI bridge 管理。

## Item/Tool UI 打开链路

### 当前链路

1. item use。
2. `IItemUIFactory.use(...)` 调用旧 `HeldItemUIFactory.INSTANCE.openUI(...)`。
3. 旧 holder 保存 `InteractionHand`。
4. `createUI(...)` 返回旧 `ModularUI`。

### 目标链路

1. item use 保留。
2. 使用 LDLib2 held item menu 或 GTM item holder bridge。
3. holder 保存 `InteractionHand` 和当前 `ItemStack` 校验信息。
4. 创建 LDLib2 root UIElement。
5. 字段修改使用 item 自身的 GT sync 能力或 item component 同步能力。

### 风险

- item UI 的 holder 有效性不仅取决于 hand，还取决于物品是否仍是原物品。
- 若 UI 中修改 item component，需要明确使用 `ItemSyncHolder` 或等价 GT sync 路径。

## UI Editor 打开链路

UI Editor 后置处理。原因：

- 当前 `GTUIEditorFactory` 直接依赖旧 `UIFactory` 和旧 editor widget。
- `.rtui` 反序列化依赖旧 configurable widget。
- UI Editor 迁移风险高，但不影响普通机器 UI 的运行。

建议：

1. 第一轮迁移中禁用或暂缓 UI Editor。
2. 主 UI 迁移完成后评估 `.rtui` 使用量。
3. 再决定转换 `.rtui`，还是重写 LDLib2 editor。

## 阶段验收

- 普通机器 UI 可以打开和关闭。
- Cover UI 可以通过 `UICoverHolder` 打开和关闭。
- Item/Tool UI 可以打开和关闭。
- 旧 `UIFactory` 引用数量减少。
- 打开 UI 不触发 LDLib2 RPC。
- 客户端 holder 失效时 UI 能关闭或显示失效状态，并记录必要日志。
