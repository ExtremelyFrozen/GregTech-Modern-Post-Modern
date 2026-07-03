---
title: 文档之外
---


# 文档之外

虽然我们会尽量保持文档最新且完整，但它不一定总是包含所有最新信息。

作为文档之外的补充资料，你也可以直接参考源代码中的 KubeJS 集成：
[`src/main/java/com/gregtechceu/gtceu/integration/kjs`](https://github.com/ExtremelyFrozen/GregTech-Post-Modern/tree/1.21/src/main/java/com/gregtechceu/gtceu/integration/kjs)

下面列出几个你可能需要查看的重要位置。


## Builders（构建器）

!!! link "Builders（构建器）"
    [`src/main/java/com/gregtechceu/gtceu/integration/kjs/builders`](https://github.com/ExtremelyFrozen/GregTech-Post-Modern/tree/1.21/src/main/java/com/gregtechceu/gtceu/integration/kjs/builders)

如果你不确定某个 builder 上有哪些可用方法和字段，可以在这个目录中找到它们。


## Material Builder（材料构建器）

!!! link "Material Builder（材料构建器）"
    [`src/main/java/com/gregtechceu/gtceu/api/data/chemical/material/Material.java`](https://github.com/ExtremelyFrozen/GregTech-Post-Modern/blob/1.21/src/main/java/com/gregtechceu/gtceu/api/data/chemical/material/Material.java)

material builder 不在 KJS 集成包中。
请改为参考嵌套的 `Material.Builder` 类。


## Bindings 与 Type Wrappers（绑定与类型包装器）

!!! link "GregTechKubeJSPlugin"
    [`src/main/java/com/gregtechceu/gtceu/integration/kjs/GregTechKubeJSPlugin.java`](https://github.com/ExtremelyFrozen/GregTech-Post-Modern/blob/1.21/src/main/java/com/gregtechceu/gtceu/integration/kjs/GregTechKubeJSPlugin.java)

- 自定义 bindings 列表请见 `GregTechKubeJSPlugin.registerBindings()`
- type wrappers 及其可接受输入列表请见 `GregTechKubeJSPlugin.registerTypeWrappers()`
