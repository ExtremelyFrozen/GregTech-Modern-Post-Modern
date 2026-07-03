# 覆盖板（Covers）
**所有**包含 BlockEntity 的 GregTech Modern 方块都可以安装 Covers，以添加额外功能或改变其常规功能。
其中部分 covers 会影响物品与流体传输。

Covers 可以通过所有 GregTech Modern 机器与 hatches 中的机器侧面配置 UI 来安装、配置或移除。它们也可以通过以下方式操作：

* 手持 cover 右键机器以安装
* 使用 Screwdriver 右键对应侧面，或空手 shift-right-click 对应侧面以配置
* 使用 Crowbar 右键以移除

物流 covers 从 LV 起的所有电压 tier 都可用，其物品与流体的最大传输速率取决于自身电压 tier。
（有一个 add-on mod 也添加了 ULV covers，但它们不属于基础 GregTech Modern。）

## 传送带模块与电动泵（Conveyor Modules and Electric Pumps）
Conveyor Modules 和 Electric Pumps 是标准物流 covers。它们可以分别放在带物品栏或流体罐的方块上，
也可以放在 item pipes 或 fluid pipes 上。放置后，它们默认处于 Export mode，会从所附着的方块中抽取物品或流体，
并推入其朝向的方块。使用 Screwdriver 可以将其切换为 Import mode，也可以配置每秒或每 tick 的传输速率。

连接到 Fluid Pipe 时，Electric Pumps 会覆盖该侧 pipe 的常规流体传输速率，流体会按照 Pump 的设置传输。

向 Item Pipe 运输物品时（无论是放在 pipe 旁并设为 export，还是放在 pipe 上并设为 import），Conveyor Modules
有一个额外功能：Distribution Mode。它有三个选项：

* Priority：物品会优先发送到最近的可用物品栏
* Round Robin：物品会近似平均地分配给所有可用物品栏
* Round Robin with Restriction：物品会分配给所有可用物品栏，但会忽略路径上存在 Restrictive Item Pipes 的目标；
除非没有其他可发送目标

## 机械臂与流体调节器（Robot Arms and Fluid Regulators）
Robot Arms 和 Fluid Regulators 是 Conveyor 与 Pump 的进阶版本。它们具有相同功能，并额外提供 Transfer Mode 切换：

* Transfer Any：始终尽可能多地传输可用物品或流体
* Transfer Exact：只有在存在**精确**数量可供运输时才传输物品或流体（例如一次只运输正好 13 个物品，或正好 144mB 流体）。
如果数量少于目标值，cover 不会传输任何内容；即使有更多可用内容，也只会传输精确数量。这有助于避免机器物品栏已满，
但每个堆叠都只有运行配方所需物品数量的一半，从而导致机器堵塞。
* Keep Exact：检查目标物品栏内容。只有当目标中每种可用物品或流体的数量低于设定量时，才会传输物品或流体；
且只传输足以让目标物品栏达到设定量的数量。这有助于防止机器被一种物品完全填满，导致没有空间容纳第二种必需物品。

安装到 Pipe 上时，Robot Arms 可以使用 Transfer Exact mode 向 pipe 输出，但无法使用 Keep Exact mode 输出。
不过，Robot Arms *可以*安装在 pipe 输出端，并会对其正在输出到的物品栏强制执行 Keep Exact 数量。

## 过滤器（Filters）
物流 covers 可以安装 Filters，以明确允许（whitelist）或禁止（blacklist）指定物品或流体通过这些 covers。
Filters 可以通过对空气右键进行配置；物品和流体既可以从物品栏中放入 filter（不会消耗），也可以从 NEI/JEI/EMI 中拖入。

Filters 有五种类型：

* Item Filter、Fluid Filter - 按特定物品或流体过滤。可以设置为检查或忽略物品 NBT 数据。
* Item Tag Filter、Fluid Tag Filter - 使用 Regular Expression 字符串过滤，通过一个或多个 Item Tags 搜索。
可以使用逻辑运算符在单个 filter 中包含或排除多个 tags。
* Smart Item Filter - 通过搜索配方逻辑中的有效原料来过滤物品。支持 Centrifuge、Electrolyzer 和 Sifter 配方列表。

Filters 也可以作为 covers 安装到设为 auto-export 或接收输入的机器面上，从而对通过该面的物品或流体应用过滤。

Fluid Filter Covers 也可以安装到 Fluid Pipes 上，用于隔离单独的 pipe 方向，并确保只有一种流体能沿该 pipe 传输
（例如有一根 Quadruple Fluid Pipe 正在承载四种流体，而其中一种需要被分离出来）。

## 末影链接（Ender Links）
除了上述所有设备之外，还有另一组 covers：Ender Link Covers。它们分为三种类型：Item、Fluid 和 Redstone。
这些 covers 在放置后必须使用 screwdriver 配置；一旦配置完成，它们就会成为跨维度无线连接的 Ender Links 网络的一部分。

Ender Links 通过 Channel 分配到某个网络；Channel 使用 8 位十六进制颜色代码（RBGA 格式），并可选填 Description
（文本字符串）。每个 Channel 都有两种形式：Public form（服务器上所有人可访问）与 Private form
（只有放置该 Link cover 的玩家可访问）。同一 channel 上同类型的所有 Links 随后会相互连接，
允许在它们之间导入或导出物品、流体或 redstone 信号。

分配 channel 后，Ender Links 拥有与 Conveyor 或 Pump 相同的输入/输出和 filter 控制。不过，单个 Ender Link cover
只能单向工作，只允许其附着的机器向 ender 网络输出，或从中输入。

每个 Ender Link channel 都包含一个存储槽：可以是一个 redstone 值、一个 160,000mB fluid tank，或一个 1-slot inventory。
每个 Ender Fluid Link cover 每秒最多可输入或输出 160,000mB 流体；每个 Ender Item Link 每秒最多可输入或输出 160 个物品，
并且每 tick 尝试传输。由于 Link Covers 只有一个槽位，因此它们最适合传送单一类型的物品或流体。

## 销毁覆盖板（Void Covers）
有时某些物品或流体是不需要或不想要的。Void Covers 就是为此而存在。任何输出到 Voiding Cover 中的物品或流体都会被直接删除。

Void Covers 有两个版本：

* Simple：销毁所有进入其中的物品或流体。无需进一步配置。
* Advanced：允许配置 Filter，只销毁特定物品或流体。还具有 Keep Exact mode，使 cover 只在所附着物品栏中的内容超过配置数量时才销毁。
