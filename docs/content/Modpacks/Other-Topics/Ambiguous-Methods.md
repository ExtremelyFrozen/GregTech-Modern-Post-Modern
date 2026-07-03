---
title: Ambiguous Methods（歧义方法）
---

## Ambiguous Methods（歧义方法）
有时在 KJS 中调用函数时会遇到 ambiguous methods。
当存在多个 overloads（例如同名但类型不同的方法）且 KubeJS 无法根据你的参数判断应该调用哪个函数时，就会发生这种情况。

例如，当你这样写：
```js

GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('unboxinator', 'multiblock')
        .tooltips(Component.literal("I am a multiblock"))
    // Rest of the multiblock
})
```

会得到以下错误：
```
Error in 'GTCEuStartupEvents.registry': The choice of Java method com.gregtechceu.gtceu.api.registry.registrate.MultiblockMachineBuilder.tooltips matching JavaScript argument types (net.minecraft.network.chat.MutableComponent) is ambiguous; candidate methods are:
    class com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder tooltips(java.util.List)
    class com.gregtechceu.gtceu.api.registry.registrate.MachineBuilder tooltips(net.minecraft.network.chat.Component[])
```

本例中，以下 2 个 Java 函数之间存在歧义：
```java
    public MachineBuilder<DEFINITION> tooltips(@Nullable Component... components) {
        return tooltips(Arrays.asList(components));
    }

    public MachineBuilder<DEFINITION> tooltips(List<? extends @Nullable Component> components) {
        tooltips.addAll(components.stream().filter(Objects::nonNull).toList());
        return this;
    }
```

你需要从两者中选择一个，可以用以下方式完成：
```js
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('unboxinator', 'multiblock')
        ["tooltips(java.util.List)"]([Component.literal("I am a multiblock")])
        // Rest of the multiblock
})
```
或者
```js
GTCEuStartupEvents.registry('gtceu:machine', event => {
    event.create('unboxinator', 'multiblock')
        ["tooltips(net.minecraft.network.chat.Component[])"]([Component.literal("I am a multiblock")])
        // Rest of the multiblock
})
```

由于 JavaScript 索引的工作方式，`.foo` 和 `["foo"]` 是同一件事，因此之后仍然可以继续链式调用函数，因为它只是一个“普通”的 builder method，只是用更具体的方式调用。

## Ambiguous Constructors（歧义构造函数）
尝试调用 constructor 时也可能遇到同样的问题。
例如，当你这样写：
```js
GTCEuStartupEvents.registry("gtceu:recipe_type", event => {
  event.create("unboxinator")
    .setProgressBar(
      new ResourceTexture("kubejs:textures/gui/progress_bar/progress_bar_stone_oreifier.png"),
      FillDirection.LEFT_TO_RIGHT
    )
    // Rest of the recipe type
})
```
会得到以下错误：
```
dev.latvian.mods.rhino.EvaluatorException: The choice of Java constructor com.lowdragmc.lowdraglib.gui.texture.ResourceTexture matching JavaScript argument types (string) is ambiguous; candidate constructors are:
    ResourceTexture(net.minecraft.resources.ResourceLocation)
    ResourceTexture(java.lang.String) (startup_scripts:example.js#17)
```

你需要从两者中选择一个，可以用以下方式完成：
```js
GTCEuStartupEvents.registry("gtceu:recipe_type", event => {
  event.create("unboxinator")
    .setProgressBar(
      ResourceTexture["(java.lang.String)"]("kubejs:textures/gui/progress_bar/progress_bar_stone_oreifier.png"),
      FillDirection.LEFT_TO_RIGHT
    )
    // Rest of the recipe type
})
```
或者
```js
GTCEuStartupEvents.registry("gtceu:recipe_type", event => {
  event.create("unboxinator")
    .setProgressBar(
      ResourceTexture["(net.minecraft.resources.ResourceLocation)"](new ResourceLocation("kubejs:textures/gui/progress_bar/progress_bar_stone_oreifier.png")),
      FillDirection.LEFT_TO_RIGHT
    )
    // Rest of the recipe type
})
```
!!! Note "注意"
    编译后的代码中不存在 generics，因此例如调用 `memoize(Supplier<T> delegate)` 会变成 `["memoize(Supplier)"](...)`
