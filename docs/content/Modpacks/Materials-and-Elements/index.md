---
title: "材料与元素"
---


# 材料与元素

GregTech 拥有自己的基于化学元素的材料系统。

Materials 由化学元素和/或其他 materials 组成。
每种 material 可以拥有不同的物品（和方块），例如 ingots、dusts、plates、wires、ores 等。

## 关于注册的说明
注册新 material 时顺序很重要。如果你通过 `.components()` 引用某个 material，必须确保其他 material 已经在当前 material 之前创建。
