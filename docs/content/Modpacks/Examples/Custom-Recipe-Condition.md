---
title: 自定义配方条件
---

!!! Warning
    自定义配方条件仅支持 Java。因此，本页只包含 Java 示例。

配方条件是为你的配方添加的自定义条件，例如生物群系、机器 tier，或任何你能想到的其他条件。

!!! Note
    条件会在配方匹配之后、配方执行之前运行。如果配方条件不匹配，机器会被挂起，并且在输入/输出发生变化之前不会再次更新。

它们通过以下方式注册：
```java
@Mod(ExampleMod.MOD_ID)
public class ExampleMod {

    // in 1.20.1
    public static RecipeConditionType<ExampleCondition> EXAMPLE_CONDITION;

    public ExampleMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addGenericListener(RecipeConditionType.class, this::registerConditions);
    }

    public void registerConditions(GTCEuAPI.RegisterEvent<String, RecipeConditionType<?>> event) {
        EXAMPLE_CONDITION = GTRegistries.RECIPE_CONDITIONS.register("example_condition", // (1)
                new RecipeConditionType<>(ExampleCondition::new, ExampleCondition.CODEC));
    }
    // end 1.20.1

    // in 1.21.1
    public static final RecipeConditionType<ExampleCondition> EXAMPLE_CONDITION = GTRegistries.register(GTRegistries.RECIPE_CONDITIONS,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MOD_ID, "example_condition"), // (2)
            new RecipeConditionType<>(ExampleCondition::new, ExampleCondition.CODEC));

    public ExampleMod(IEventBus modBus, FMLModContainer container) {
        modBus.addListener(CommonInit::onRegister);
        bus.addListener(RecipeConditionType.class, this::registerConditions);
    }

    public void registerConditions(GTCEuAPI.RegisterEvent<String, RecipeConditionType<?>> event) {
        EXAMPLE_CONDITION = GTRegistries.RECIPE_CONDITIONS.register("example_condition",
                new RecipeConditionType<>(ExampleCondition::new, ExampleCondition.CODEC));
    }
    // end 1.21.1
}
```

1. 1.20.1 版本不要求 namespace，因此请确保不要使用与他人相同的 ID！
2. 你可以使用类似 `GTCEu.id` 的辅助方法来创建 ResourceLocation，但你**必须**为它使用自己的 namespace。

接下来会设置一个条件，要求机器的能量缓存位于某个 Y 坐标以上。
```java
public class ExampleCondition extends RecipeCondition<ExampleCondition> {

    public static final Codec<ExampleCondition> CODEC = RecordCodecBuilder.create(instance -> RecipeCondition.isReverse(instance)
            .and(Codec.INT.fieldOf("height").forGetter(val -> val.height)
    ).apply(instance, ExampleCondition::new));

    public int height;

    public ExampleCondition(boolean isReverse, int height) {
        this.isReverse = isReverse;
        this.height = height;
    }

    public ExampleCondition(int height) {
        this(false, height);
    }

    public ExampleCondition() {
        this(false, 0);
    }

    @Override
    public RecipeConditionType<ExampleCondition> getType() {
        return ExampleMod.EXAMPLE_CONDITION;
    }

    @Override
    public Component getTooltips() {
        return Component.literal(String.format("Should be ran at least at height %d", height));
    }

    @Override
    protected boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        return recipeLogic.getMachine().getHolder().getCurrentPos().getY() >= height;
    }

    @Override
    public ExampleCondition createTemplate() {
        return new ExampleCondition(0);
    }
}
```

下面逐步说明这个示例。说明顺序不会完全按照文件中的顺序，而是按照更容易理解的顺序展开。

先从这里开始：
```java
    @Override
    public RecipeConditionType<ExampleCondition> getType() {
        return ExampleMod.EXAMPLE_CONDITION;
    }

    @Override
    public Component getTooltips() {
        return Component.literal(String.format("Should be ran at least at height %d", height));
    }
```
这一部分很简单，只是返回该条件的 type 和 tooltip。如果存在这个条件，该 tooltip 会被添加到配方查看器界面中。

```java
    public ExampleCondition(boolean isReverse, int height) {
        this.isReverse = isReverse;
        this.height = height;
    }

    public ExampleCondition(int height) {
        this(false, height);
    }
```
这些是构造器。这里需要 `isReverse`，因为它是上层 `RecipeCondition` 类型的一部分。`isReverse` 表示如果条件满足，你的配方反而不会运行。此外，为了（反）序列化，还需要一个包含所有参数的构造器。

```java
    @Override
    public ExampleCondition createTemplate() {
        return new ExampleCondition(0);
    }
```

这会创建可能用于序列化的基础 "template"。它应当返回你的条件的默认版本。

```java
    @Override
    protected boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        return recipeLogic.getMachine().getHolder().getCurrentPos().getY() >= height;
    }
```

这就是实际条件。

```java
    public static final Codec<ExampleCondition> CODEC = RecordCodecBuilder.create(instance -> RecipeCondition.isReverse(instance).and(
            Codec.INT.fieldOf("height").forGetter(val -> val.height)
    ).apply(instance, ExampleCondition::new));

    public int height;
```

CODEC 告诉 Java 如何序列化/反序列化你的条件。它用于客户端/服务端之间的同步，也用于将条件存储到 JSON 中，以便世界加载时读取。
它由几个部分组成：

- `RecordCodecBuilder.create(instance -> ` 表示我们会启动一个 RecordCodecBuilder，也就是一个只由简单类型组成的构建器。
- `RecipeCondition.isReverse(instance)` 是一个辅助 codec，用于序列化你的 codec 中的 isReverse boolean。
- `.and(` 允许向 codec 添加额外字段。
- `Codec.INT.fieldOf("height").forGetter(val -> val.height)` 表示我们想序列化一个整数，并在 JSON 中把它称为 "height"；要获取待序列化的值，则使用 `ExampleCondition#height`。
- `.apply(instance, ExampleCondition::new)` 表示反序列化回对象时，会应用这些步骤得到值（此处为 `bool isReverse, int height`），并用这些参数调用构造器。
    在这个示例中，它会调用前面定义的 `new ExampleCondition(isReverse, height)` 构造器。

有了这些内容，你就具备了创建自定义 RecipeCondition 所需的一切。

要将它应用到 recipeCondition，可以向 Recipe Builder 添加：`.condition(new ExampleCondition(70))`，表示要求高度为 70