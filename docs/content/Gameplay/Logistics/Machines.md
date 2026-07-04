# 机器物流
## 蒸汽机器（Steam Machines）
Steam Machines 没有任何内置物流能力，只有三个例外：

* Primitive Water Pump 是一台 Multiblock Machine，并包含一个 Output Hatch。该 hatch 会自动将水推入任何已连接的 pipe 或 tank。
* Coke Oven 也是一台 Multiblock machine，但与其他 multiblocks 不同，它的 controller 确实包含自己的物品栏。
  为了辅助 Coke Oven 自动化，可以在其结构中放置一种名为 Coke Oven Hatch 的独特方块；它会接受物品输入，并自动输出物品与流体。
* 所有含有 Steam 的 Boilers 都会尝试向相邻的 fluid pipes 或机器输出 Steam，方向包括_除下方之外_的所有侧面。
  这意味着可以安全地从下方输入水，而不会有 Steam 进入供水 pipes 的风险。

此外，Steam machines 还有一个独特的设计挑战：所有 Steam machines 都有一个 Exhaust face，它必须朝向开放空气，
并会在机器每次完成配方时喷出一股 Steam。如果 Exhaust face 被阻挡，机器就无法完成配方；
喷出的 Steam 也会重伤站在其中的玩家。

## 电力机器（Electric Machines）
所有 Electric machines 都能将产出的物品、流体或二者自动输出到任何相邻的机器、物品栏、tank 或 pipe 中。
这可以通过 Wrench 完成，也可以通过机器 UI 中的 Side Configuration 标签页完成。

* 要从机器外部更改 auto output，请手持 Wrench 对空气 shift-right-click，选择要配置 Items、Fluids 还是 Both，
  然后用 Wrench 右键机器侧面以旋转输出面。
  * 随后要启用自动输出，请用 Screwdriver 右键机器面。
* 要从机器内部更改 auto output，请打开 Side Configuration 标签页，并点击要设为输出的侧面。
  * 第一次点击会选择该侧面，第二次左键点击会将该侧面设为物品输出，第三次左键点击会切换 auto output。
    右键点击则会设置和切换流体输出。
  * 主机器 UI 还包含两个切换按钮，可以在不打开配置面板的情况下启用或禁用物品或流体的 auto output。

此外，所有 electric machines 都会从输出侧**阻止**自动化物品或流体输入。若要覆盖此行为并允许从输出侧输入，
可以使用 Side Configuration 标签页中的额外切换按钮，或用 Screwdriver 对机器 shift-right-click。

## 多方块机器（Multiblock Machines）
Multiblock machines 的 controller blocks 内不存放物品；所有 I/O 都由 Buses 和 Hatches 处理。默认情况下，
Buses 和 Hatches 会从其朝向的任何物品栏、tanks 或 pipes **auto-import**，并向物品栏、tanks 或 pipes **auto-export**。
可以通过 UI 中的电源按钮或用 Soft Mallet 右键来 Disable hatch，从而切换这一行为。

Buses 和 Hatches 也可以接受来自其他侧面的自动化导入或导出，只要有其他东西在推动该操作即可。

### 独立、染色与过滤输入输出（Distinct、Painted 与 Filtered） { #distinct-painted-and-filtered-inputs-and-outputs }
通常情况下，multiblock machine 上**所有**输入 Buses 和 Hatches 都会被检查以寻找配方输入，
所有输出 buses 和 hatches 都会用于放置配方输出。然而，当用户希望单台机器执行多个配方时，这可能导致不想要的行为：
这些配方的原料可能互相冲突，并被组合成另一个不想运行的第三种配方。此外，当机器以这种方式使用时，
产出的物品或流体会被送往哪个 output bus/hatch 选择得相当随意，使得规划 pipes 来运走特定输出产物变得困难且笨重。

GregTech Post Modern 为此提供了三种工具：Fluid Hatch Filter Locking、Distinct Buses 与 Painted Buses/Hatches。

* Fluid Hatch Filter Locking 是一个简单系统，用于解决哪些 output hatches 接收哪些产出流体的问题。
使用与 Super Tank 相同的界面，可以将 Fluid Output Hatch 当前包含的流体 Locked，意味着只有该流体会被放入其中；
也可以通过从 JEI/EMI 将流体拖入 Hatch 的输出槽，预先将 Hatch 锁定到某种流体。
* Filter Locking 只适用于标准的*单槽* fluid hatches，不能用于更高 tier 的 Quadruple 或 Nonuple Fluid Hatches。
（不过，这些 Hatches 本来也不能在多个槽中容纳同一种流体，因此与 Quadruple 或 Nonuple Fluid Pipes 搭配时，
仍然允许一定程度的输出分离。）
* Distinct Buses 是 Input Buses（不是 Hatches）上的一个切换项，会让机器将该 bus 视为与所有其他 Distinct Buses 分离。
（单个 distinct bus 没有意义，但同一台机器上有两个 distinct buses 时，机器会分别搜索每个 distinct bus。）
* Painted Buses/Hatches 是使用 spray paint 染色过的 hatches。机器搜索配方输入时，会把相同颜色的 Input Buses/Hatches
放在一起检查；但不同颜色的 buses/hatches 中存放的任何物品或流体不会参与此次搜索。这种隔离与 Distinct Buses 相同，
但允许多个 buses/hatches 作为一个组一起搜索。
* 在 7.5.0 之前，给 **Output** Buses/Hatches 染色没有效果。7.5.0 引入了机器将 Painted Outputs 与 Painted Inputs
配对的行为：如果某个配方从 Painted Input 拉取物品，它的产物只能输出到相同颜色的 Painted Output（或未染色输出）。
* 未染色或未设为 Distinct 的 Buses 和 Hatches 对机器来说始终可用：Distinct 与 Painted Inputs 始终可以从其他非 distinct、
非 painted 输入拉取内容；使用了 Painted Inputs 中原料的配方，也始终可以把产物送往非 painted 输出。

## 穿墙 Hatch 与 Cleanroom（Passthrough Hatches） { #passthrough-hatches-and-cleanroom }
Cleanroom 是一种带有独特限制的 multiblock。由于 Cleanroom 必须拥有实体墙，外部 pipes、cables 和物品栏无法直接连接到内部机器。
Passthrough Hatches 就是为此存在。Passthrough Hatches（默认情况下只有 HV Passthrough Hatches 可合成）
是可以放在 cleanroom 墙壁或地板中的实体方块。这些 hatches 同时充当 Input 与 Output，会在同一 tick
从绿色面 auto-Import，并向红色面 auto-Export。这允许物品和流体穿过 cleanroom 墙壁进出。

此外，由于 Generators 不能放在 Cleanroom 内部，可以使用 Diodes 将电力传入。Diodes 也可以放在墙壁或地板中；
用 Soft Mallet 右键时，会循环限制可通过的 Amps 数量：1/2/4/8/16A。

最后，Machine Hulls 有一个独特功能。一方面，它们可以作为 1A Diode 使用并传输单个 amp 的电力。
另一方面，它们也会被 Applied Energistics ME Network 视为有效 cables，因此 ME Cables 会连接到 Hulls，
并允许 ME Network 延伸到 Cleanroom 内部。
