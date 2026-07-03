---
title: Element 创建
---


## Element 创建
!!! Note
    你只能添加元素周期表中尚不存在的 elements。
    对于这些 elements，请参见 GTElements。
Elements 是 GT materials 的基础。注册 element **不会**添加任何物品。

```js
GTCEuStartupEvents.registry('gtceu:element', event => {
   event.create('test_element')
        .protons(27)
        .neutrons(177)
        .halfLifeSeconds(-1)
        .decayTo(null)
        .symbol('test')
        .isIsotope(false)
})
```

1.  `.create(String name)` -> element 名称。
2.  `.protons(int protons)` -> proton 数量。如果它是不会获得 material 的 element，请使用 `-1`。
3.  `.neutrons(int neutrons)` -> neutron 数量。如果它是不会获得 material 的 element，请使用 `-1`。
4.  `.halfLifeSeconds(int seconds)` -> 半衰期秒数。N 秒后，一半 material 会衰变。如果 element 不会衰变，请使用 `-1`。
5.  `.decayTo(Material material)` -> 衰变目标 material。如果 element 不会衰变，请使用 `null`。
6.  `.symbol(String symbol)` -> 原子符号，会显示在化学式中。
7.  `.isIsotope(boolean isotope)` -> 该 element 是否为 isotope，例如 Uranium 235 和 Uranium 238。

当从此 element 创建 material 时，上述属性会影响自动生成的配方。
