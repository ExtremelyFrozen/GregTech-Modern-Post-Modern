---
title: "TagPrefixes 与 .setIgnored()"
---


# TagPrefixes 与 `.setIgnored()` 的作用

## 什么是 TagPrefix？

TagPrefixes 是 GTCEu Modern 用来简化给 Materials 应用物品和方块 tags 的方式，同时还承担一些其他
功能。`TagPrefix` class 可在 startup 和 server scripts 中使用，其中包含许多预定义的
TagPrefixes，可以把从钻头到无瑕宝石等各种对象与某个 Material 关联起来。

一个常见且容易理解的例子是 `TagPrefix.ingot`，它会关联所有拥有
IngotProperty 并因此拥有对应锭物品的 Materials，包括通过 KubeJS 定义的自定义 Materials。
TagPrefixes 提供本地化、物品和方块 tagging，并影响许多合成配方，是
GTCEu 的 Material 定义系统能够正常工作的核心组成部分。

!!! tip "有哪些 TagPrefixes？"
所有可用 TagPrefixes 的列表可以在 GTCEu Modern 的 GitHub 中找到，对应 class 为 `TagPrefix`。


## 什么是 `.setIgnored()`？

在浏览 GTCEu Modern 代码库或单纯游玩 Minecraft 时，你可能已经注意到 GTCEu Modern
会对部分原版材料做特殊处理。例如铁锭是原版物品，但 GTCEu Modern 不会像其 Material 定义所暗示的那样
再创建一个重复的铁锭。

相反，GTCEu Modern 的铁 Material 条目会将原版铁锭视为该 Material 的锭，因此
不会产出重复物品。
此功能由 TagPrefixes 管理，整合包作者也可以将它用于自己的自定义物品，或用于编写
GTCEu Modern 与其他 mod 之间的兼容。


## 好的，那该如何使用？

此功能可以在 material modification event 中使用，这是一个 startup event。
material modification event 会在 Minecraft 启动流程中，于 Material 注册完成之后、
Material registry 关闭之前触发；你无法用它定义新的 Materials。

每个 TagPrefix 都可以使用以下调用：

- `.setIgnored()`，带一个输入参数：
  接受一个 `Material` 作为输入，并阻止 GTCEu 将该特定 TagPrefix 与该 Material 关联。
- `.setIgnored()`，带两个输入参数：
  接受一个 `Material` 和一个 `Item` 或 `Block`（或任何实现 `ItemLike` interface 的 class）作为输入；
  使 GTCEu 将传入的 `ItemLike` 视为该 TagPrefix 原本会为该 Material 生成的物品。
  也可以以 JS 数组形式传入 `ItemLike...` varargs，从而一次对多个方块和/或物品执行该操作。
- `.removeIgnored()`：
  接受一个 `Material` 作为输入，并重新启用与该 TagPrefix 关联的该 Material 物品生成。

!!! caution "注意 `Item.of()`！"
KubeJS 中获取 `Item` 的经典方式，也就是 `Item.of()` wrapper，在这里不起作用。
你需要直接传入来自 Java class 的 `ItemLike`，`.setIgnored()` 才能正确工作。

下面是一个使用 Applied Energistics 2 物品的更直观示例：

```js title="setignored_usage_example.js"
GTCEuStartupEvents.materialModification(event => { // (1)
    TagPrefix.gemChipped.setIgnored(GTMaterialRegistry.getMaterial("fluix_crystal")) // (2)
    TagPrefix.rock.setIgnored(GTMaterialRegistry.getMaterial("sky_stone"), AEBlocks.SKY_STONE_BLOCK) // (3)
    TagPrefix.ingot.removeIgnored(GTMaterials.Iron) // (4)
})
```

1. 此 event 没有 `event.create()` 之类的方法，因为它并不用于创建任何内容，只用于调整
   已存在的 Material 关联。事实上，此 event 完全没有可访问的方法。
2. 此调用会阻止 GTCEu Modern 为自定义 `fluix_crystal` Material 创建 chipped gem 变种。
3. 此调用会使 GTCEu Modern 将 AE2 的 Sky Stone 方块作为 rock 类型（类似原版石头与
   GTCEu Modern 的石头 `Material` 的关联方式）关联到自定义 `sky_stone` Material。根据 mod 作者
   如何向 KubeJS 暴露其 mod class，你可能需要手动加载包含希望与 `Material` 关联的 `ItemLike` 的数据定义 class。
4. 此调用会让 GTCEu Modern 解除原版铁锭与 GTCEu Modern 的铁 Material 条目之间的关联，导致它
   生成一个重复的铁锭。

你要调整 TagPrefix 的 `Material` 必须已经注册到 GTCEu Modern 的 Material registry 中；如果该
Material 是自定义的，则需要像这些文档所示，使用 `GTCEuStartupEvents.registry()` 完成注册。
