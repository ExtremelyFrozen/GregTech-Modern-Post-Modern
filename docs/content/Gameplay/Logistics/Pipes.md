# 管道
GregTech Modern 提供 Item Pipes（物品管道）与 Fluid Pipes（流体管道），它们可以由多种材料制成，并覆盖多种容量规格。

## 放置管道
管道（Pipes）与线缆（Cables）使用一套共享且独特的世界内放置系统。

默认情况下，放置在世界中的 Pipe 或 Cable 不会连接到任何相邻方块。不过，如果你对着机器或另一根 Pipe
按住 Shift 右键放置 Pipe，它会以已连接到该机器或 Pipe 的状态被放下。

放下一根 Pipe 后，手持另一根 Pipe 看向它时，会显示与手持 Wrench 或其他工具看向机器时相同的侧面覆盖层。
在某个侧面区域内手持 Pipe 右键，会在*现有 Pipe 的对应侧面*放置一根新的、已连接的 Pipe。
这样你无需站在管线延伸路径上，也能连续铺设管线。

### 框架方块（Frame Boxes）
Pipes 可以放入 Frame Boxes，Frame Boxes 也可以覆盖在 Pipes 外。这主要是审美选择，不过它也会强制 Pipe
的碰撞箱占满一个完整方块空间，因此能在一定程度上防止玩家碰到装有极热或极冷流体的 Fluid Pipe 所带来的危险（见下文）。

## 管道连接
可以用 Wrench 右键 Pipes，以连接或断开该侧的 Pipe。这可用于预先设置管道连接，为稍后在该位置放置机器做准备；
也可用于连接或断开需要从其他管线分支出去的 Pipe 段。

此外，也可以对 Pipes 按住 Shift 右键，将指定侧面设为 "Shutter"。被 Shutter 的 Pipe 侧面会变成
**仅输出**，从而让 Pipes 变为单向。被 Shutter 的 Pipe 侧面会显示一个小黑箭头，箭头方向就是允许传输的方向。

Pipes 对内容如何分配到各个连接面有默认逻辑，不过这些行为可以通过 [Covers](./Covers.md) 修改。

## 管道材料与尺寸
Pipes 有四种尺寸：Small、Normal、Large 和 Huge。Fluid pipes 还额外提供 Tiny 尺寸。尺寸越大，成本越高，
吞吐量也越大。Pipe 的吞吐量由其材料决定（通常需要更高电压 tier 才能生产的材料拥有更高吞吐量），再乘以尺寸倍率。

## 物品管道（Item Pipes）
Item Pipes 实际上像是连接来源与目标的传送通道：任何被推入 Pipe 的物品都会立刻从另一端输出。Item Pipes
可以是一对一、一对多、多对一或多对多。Item pipes 对每秒可通过的物品数量有限制。整条 pipe 的限制取决于路径上最小的管段
（虽然物品是传送过去，而不是逐个 Pipe 方块移动，但它们仍会沿着管道路径前进并检查路径上的每根 Pipe）。
可同时通过 Pipe 的不同物品类型数量没有限制，限制只作用于每秒总数量。

物品被推入 pipe 时，默认会被送往距离来源“最近”的物品栏。不过，这里的“最近”并不是由来源与目标之间的方块距离直接决定的，
而是由 Pipe 的 "Priority" 值决定。更大的 Pipes 优先级更低，更小的 Pipes 优先级更高，物品会选择总 "Priority" 值最小的路径。

### 限制物品管道（Restrictive Item Pipes）
Item Pipes 有一个特殊变体，称为 Restrictive Item Pipes。Restrictive Pipes 的优先级值是同等非限制 Pipe 的 100 倍，
这可以强制让某条 Pipe 路径成为“最长”路径，因此成为物品最后才会选择的路径。

## 流体管道（Fluid Pipes）
Fluid Pipes 的工作方式与 Item Pipes 完全不同。每个 Fluid Pipe 方块都是一个小型流体罐，容量等于该 Pipe 标称
Transfer Rate 的 20 倍。

每 5 ticks（每秒 4 次），Fluid Pipes 会检查相邻连接，并尝试输出最多为自身最大存储容量一半的流体。
这些流体会按连接端的充满比例，分配给自上次输出以来*没有向该 Pipe 输入过流体*的所有相邻连接。

因此，虽然 Fluid Pipes 的吞吐量以 mB/t 标注，但它们并不是每 tick 都传输，而是每秒数次传输较大批量的流体。

进一步来说，如果一条 Fluid Pipes 管线或网络没有完全填满，流体可能会在 Pipes 内来回晃动，导致某些位置更满、另一些位置更空。
可以通过对 Pipes 使用 Shutter 防止回流来缓解这一问题。

## 多重管道
Item Pipes 可以一次传输不限数量的物品类型，但 Fluid Pipes 不行；尝试让多种流体通过同一根 Pipe 很容易让某种流体卡在某处。
为解决这个问题，可以将 4 根 Small Pipes 或 9 根 Tiny Pipes 合成为 Quadruple 或 Nonuple Fluid Pipe。
这些 Pipes 相当于在单个方块内包含 4 根或 9 根独立管道。每根管道都会独立计算 I/O，但**不能容纳重复流体**，
因此可以让 4 种或 9 种流体无堵塞风险地穿过一个方块。

## 危险
Fluid Pipes 还具有一组额外属性：Max Temperature 与 Fluid Containment。

GregTech Modern 中所有流体都有 Temperature，其中一些还具有额外属性：
* Acids
* Gases
* Cryogenics
* Plasmas

如果 Pipe 的 Max Temperature 低于其中流体的温度，Pipe 会间歇性地清空部分内容物，重伤附近玩家、蔓延火焰，并很快被摧毁。
例外情况是：如果 Pipe 携带的是 Plasma，且该 Pipe 标记为能够容纳 Plasmas，则会忽略温度限制。

对于其他属性，如果容纳方式不当，效果同样危险。Acids 和 Cryogenics 会伤害周围环境，然后爆炸。
Gases 位于无法承载它们的 pipes 内时不会摧毁 pipe，但气体会快速逸散并造成小型爆炸。

最后，携带极热或极冷流体（高于 320K 或低于 260K）的 pipes 会伤害碰到它们的实体，每秒两次；
伤害会随温度或寒冷程度的极端程度而提高。
