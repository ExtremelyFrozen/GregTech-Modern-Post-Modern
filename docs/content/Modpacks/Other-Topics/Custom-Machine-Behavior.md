---
title: 自定义 Machine Behavior
---

!!! Warning "警告"
    Custom Machine Behavior 目前仅在 Java 中受支持。

有时，你会想在 Machine 中执行一些无法通过 Recipe Conditions 或 Recipe Modifiers 实现的操作。这时，Custom Machine Behavior 可能就是合适的工具。

它通过注册一个自定义 `TickableSubscription` 工作，该 subscription 会在每个 tick 被调用。
这里我们要制作一个 greenhouse，它在运行时还会把自身上方的所有 dirt 变成 grass。
```java

public class Greenhouse extends WorkableElectricMultiblockMachine {

    private TickableSubscription tickSubscription;

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        if (!isRemote()) {
            tickSubscription = this.subscribeServerTick(this::turnGreenery);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        if (!isRemote()) {
            tickSubscription.unsubscribe();
            tickSubscription = null;
        }
    }

    private void turnGreenery(){
        if(!getRecipeLogic().isActive()) return;
        BlockPos currentPosition = getRecipeLogic().getMachine().getHolder().getCurrentPos();
        BlockPos abovePosition = currentPosition.above();
        if(this.getLevel().getBlockState(abovePosition).equals(Blocks.DIRT.defaultBlockState())){
            this.getLevel().setBlock(abovePosition, Blocks.GRASS.defaultBlockState(), 3);
        }
    }
}
```
`tickSubscription` 字段是当前应当每 tick 调用一次的 subscription 引用。创建该 subscription 时，我们会告诉服务端每 tick 运行一次 `this.turnGreenery()`。

在该方法中，我们只需检查 recipe logic 是否处于活动状态；如果是，并且 Machine 上方的 Block 已经是 dirt，就将它变成 grass。

要使用它，可以这样写：
```java
    public static final MultiblockMachineDefinition GREENHOUSE = REGISTRATE
        .multiblock("green_house", Greenhouse::new)
        .rotationState(RotationState.ALL)
        .recipeType(MyRecipeTypes.GREENHOUSE)
        .recipeModifiers(OC_PERFECT_SUBTICK, BATCH_MODE)
        .appearanceBlock(CASING_PTFE_INERT)
        .pattern(definition -> {
            var casing = blocks(CASING_PTFE_INERT.get()).setMinGlobalLimited(10);
            var abilities = Predicates.autoAbilities(definition.getRecipeTypes())
                    .or(Predicates.autoAbilities(true, false, false));
            return FactoryBlockPattern.start()
                    .aisle("XSX", "XXX", "XXX")
                    .aisle("XXX", "XXX", "XXX")
                    .where('S', Predicates.controller(blocks(definition.getBlock())))
                    .where('X', casing.or(abilities))
                    .build();
        })
        .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_inert_ptfe"),
                GTCEu.id("block/multiblock/large_chemical_reactor"))
        .register();
```
