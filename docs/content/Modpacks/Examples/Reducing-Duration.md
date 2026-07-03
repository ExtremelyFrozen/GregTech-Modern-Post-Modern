---
title: "机器耗时缩减"
---


# 缩短所有机器配方的耗时

## 缩短耗时脚本

```js title="Reduce_Duration.js"
ServerEvents.recipes(event => {
    event.forEachRecipe({ mod: 'gtceu' }, recipe => { // (1)
        try { // (2)
            var newDuration = recipe.get("duration") // (3)
            recipe.set("duration", newDuration/10) // (4)
        } catch (err) { // (5)
            console.log(recipe.id + " has no duration field, skipped.")
        }
    })
})
```

1. 对 GregTech 中每个配方运行代码的函数。
2. 使用 try 来跳过没有 duration 的配方，例如合成配方。
3. 获取当前 duration，存入准备修改的变量。
4. 将配方的 duration 修改为原 duration 的十分之一。
5. 如果配方没有 duration，则捕获错误并记录日志。