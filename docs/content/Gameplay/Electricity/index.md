# 电力
GregTech Post Modern 中绝大多数机器都依靠 Electricity 运行，也就是 EU（"Energy Units"）。Electricity 与
Electric machines 共享若干常见的安全和行为规则。

## EU 基础概念
* EU 由 [**Generators**](./Generators.md) 每 tick 产生。
* [**Electric Machines**](./Machines.md) 运行时每 tick 消耗 EU。
* Batteries 和 Battery Buffers 作为 EU [**存储**](./Energy-Storage.md) 使用。
* [**Cables and Transformers**](./Cables-and-Transformers.md) 在 generators、storage 与 machines 之间运输 EU。

Batteries 和 Machines 会在内部缓冲区存储 EU，但所有 EU 运输都通过 **Voltage** 与 **Amperage** 完成。

* Voltage（V）是设备的电力 tier，也是 Generators 发出、Machines 接收的能量“包”的大小。
* Cables 和 Machines 都有 voltage tier，代表它们可以安全承载或接收的最大电压。
承载或接收不安全的 Voltages 可能造成极具破坏性的后果。
    * Tiers 使用两个或三个字母的缩写表示。按顺序完整列表如下：
        * ULV, LV, MV, HV, EV, IV, LuV, ZPM, UV, UHV, UEV, UIV, UXV, OpV, MAX
    * 每个更高的 voltage tier 都是前一个 tier 电压的 4 倍。（LV = 32V，MV = 128V，HV = 512V……）
    * Transformers 可用于将某个 voltage tier 的电力转换为上一档电压，或反向转换。
* Amperage（A）表示同时并行承载多少个 Voltage 包。
* Voltage x Amperage 得到 **EU/t**。EU/t x 时间 得到 **Total EU**。
* 发出 EU 的方块只会从一个指定输出侧发出 EU，该侧通常标有一个较大的彩色圆点。
接收 EU 的方块可以从任何不是 EU Output side 的侧面接收 EU。能够发出多个 Amps 的方块，其输出侧会有更大、更复杂的圆点。
