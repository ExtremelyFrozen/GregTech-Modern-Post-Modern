---
title: "NBT Predicate Ingredients（NBT 谓词原料）"
---

在某些使用场景中，Partial 或 Strict NBT Ingredients 提供的控制力不够。这时可以使用 NBT Predicate Ingredients。
该系统允许你在配方匹配期间查询 NBT 内容，以对 ItemStacks 执行更高级的查询验证。

!!! note
    要在游戏内测试你的物品，可以使用 give 和 ftblibrary 命令，例如 `/give @p dirt{"attributes": {"strength":16, "sound":"crunch.wav" } }` 给自己一个带自定义 NBT 的物品，或使用 `/ftblibrary nbtedit hand` 打开图形编辑器。
## 用法
### 相等判断
为 JavaScript 制作了自定义重载：

- `.eqString(key, value)`
- `.eqInt(key, value)`
- `.eqFloat(key, value)`
- `.eqByte(key, value)`
- `.eqDouble(key, value)`
- `.eqTag(key, value)`

这些也都有对应的 `.neq[...](key, value)` 函数。
在 Java 中，这些同样可用，也可以使用更简单的 `.[n]eq(key, [type] value)` 重载。


=== "JavaScript"
    ```js title="gt_recipes.js"

    ServerEvents.recipes(event => {
        event.recipes.gtceu.assembler('test_nbt')
            .inputItemNbtPredicate('minecraft:dirt', NBTPredicates.eqString("charge", "23"))
            .itemOutputs('minecraft:stick')
            .duration(100)
            .EUt(30)
    })

    ```

=== "Java"
    ```java title="GTRecipes.java"

    public static void init(Consumer<FinishedRecipe> provider) {
        ASSEMBLER_RECIPES.recipeBuilder("test_nbt")
                .inputItemNbtPredicate(new ItemStack(Items.dirt, 1), NBTPredicates.eq("charge", "23"))
                .outputItems(new ItemStack(Items.STICK))
                .duration(100)
                .EUt(30)
                .save(provider);
    }

    ```

### 数字比较
存在以下数字比较运算符：

- `.lte(key, number)`: 小于等于
- `.lt(key, number)`: 小于
- `.gte(key, number)`: 大于等于
- `.gt(key, number)`: 大于

=== "JavaScript"
    ```js title="gt_recipes.js"

    ServerEvents.recipes(event => {
        event.recipes.gtceu.assembler('test_nbt')
            .inputItemNbtPredicate('minecraft:dirt', NBTPredicates.lt("charge", 23))
            .itemOutputs('minecraft:stick')
            .duration(100)
            .EUt(30)
    })

    ```

=== "Java"
    ```java title="GTRecipes.java"

    public static void init(Consumer<FinishedRecipe> provider) {
        ASSEMBLER_RECIPES.recipeBuilder("test_nbt")
                .inputItemNbtPredicate(new ItemStack(Items.dirt, 1), NBTPredicates.lt("charge", 23))
                .outputItems(new ItemStack(Items.STICK))
                .duration(100)
                .EUt(30)
                .save(provider);
    }

    ```


### 任一/全部
存在以下列表运算符：

- `.all(NBTPredicate...)`
- `.any(NBTPredicate...)`

=== "JavaScript"
    ```js title="gt_recipes.js"

    ServerEvents.recipes(event => {
        event.recipes.gtceu.assembler('test_nbt')
            .inputItemNbtPredicate('minecraft:dirt',
                NBTPredicates.all([
                    NBTPredicates.lt("charge", 23),
                    NBTPredicates.eqString("color", "blue")
                ]))
            .itemOutputs('minecraft:stick')
            .duration(100)
            .EUt(30)
    })

    ```

=== "Java"
    ```java title="GTRecipes.java"

    public static void init(Consumer<FinishedRecipe> provider) {
        ASSEMBLER_RECIPES.recipeBuilder("test_nbt")
                .inputItemNbtPredicate(new ItemStack(Items.dirt, 1),
                    NBTPredicates.all([
                        NBTPredicates.lt("charge", 23),
                        NBTPredicates.eqString("color", "blue")
                    ]))
                .outputItems(new ItemStack(Items.STICK))
                .duration(100)
                .EUt(30)
                .save(provider);
    }

    ```


### 取反
存在以下取反运算符：

- `.not(NBTPredicate)`


=== "JavaScript"
    ```js title="gt_recipes.js"

    ServerEvents.recipes(event => {
        event.recipes.gtceu.assembler('test_nbt')
            .inputItemNbtPredicate(new ItemStack(Items.dirt, 1),
                NBTPredicates.not(
                    NBTPredicates.all([
                        NBTPredicates.lt("charge", 23),
                        NBTPredicates.eqString("color", "blue")
                    ])
                )
            )
            .itemOutputs('minecraft:stick')
            .duration(100)
            .EUt(30)
    })

    ```

=== "Java"
    ```java title="GTRecipes.java"

    public static void init(Consumer<FinishedRecipe> provider) {
        ASSEMBLER_RECIPES.recipeBuilder("test_nbt")
                .inputItemNbtPredicate(new ItemStack(Items.dirt, 1),
                    NBTPredicates.not(
                        NBTPredicates.all([
                            NBTPredicates.lt("charge", 23),
                            NBTPredicates.eqString("color", "blue")
                        ])
                    )
                )
                .outputItems(new ItemStack(Items.STICK))
                .duration(100)
                .EUt(30)
                .save(provider);
    }

    ```


### Key 路径导航
可以使用 `.` 导航嵌套 tags，并使用 `[i]` 索引列表。因此：
```
{ "machine":
    { "states" :
       [
          {"color": "green"},
          {"color": "red"},
       ]
    }
}
```
会匹配：
`.eq("machine.states[0].color", "green")`
