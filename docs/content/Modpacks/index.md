---
title: 整合包制作
---


# 整合包制作

GTCEu Modern 为可定制性提供了广泛的 KubeJS 集成。
面向整合包作者的大多数工具都围绕这个 KubeJS API 展开。

请参考本节了解如何使用这些工具，以及相关示例。


## 通用说明

有时，在添加或修改内容时必须调用某个特定方法。
这些方法会在文档中用 `// [*]` 标记，例如：

```js
ServerEvents.exampleEvent(event => {
    event.create('example', builder => {
        builder.requiredMethod(42) // [*] (1)
        builder.otherRequiredMethod(42) // [*]

        builder.optionalMethod() // (2)
    })
})
```

1. 这些方法是必需的
2. 这个方法是可选的，并不需要在所有情况下调用


## 文档之外

虽然我们会尽量保持文档最新且完整，但它不一定总是包含所有最新信息。

也请查看 [文档之外](./Beyond-The-Docs.md) 页面获取更多参考。
