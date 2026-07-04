---
title: 配方条件
---

Recipe Conditions（配方条件）是配方属性，可以基于某些条件阻止配方启动，例如 Biome、Weather、Quest Completions，或你自己制作的自定义 Conditions。

这些 conditions 可以同时用于 Java 和 KubeJS 配方。不过，自定义 conditions 只能在 Java addons 中实现。如果你想了解如何制作它们，请查看 [Custom Recipe Condition](../Examples/Custom-Recipe-Condition.md) 示例页面。

!!! Note
    condition 会在配方匹配后、配方执行前运行。如果 recipe condition 不匹配，机器会被暂停，并且在输入/输出发生变化前不会再次更新。

### 基础条件

- Biome: `.biome("namespace:biome_id")`
    - 将配方限制为只能在特定 biome 中运行，适用于整合包加载的任意 biome。
    例如可以使用 `minecraft:plains`。我们也有 `biomeTag("minecraft:biome")`。
- Dimension: `.dimension("namespace:dimension_id")`
    - 将配方限制为只能在特定 dimension 中运行，gas collector 就是一个很好的例子。
    - 例如可以使用 `.dimension("minecraft:the_end")`
- Y Position: `.posY(int min, int max)`
    - 将配方限制为只能在世界中的特定 y level 运行。
    - 例如可以使用 `.posY(120, 130)`，要求机器位于 y 120 到 y 130 之间。
- Rain: `.rain(float level)`
    - 将配方限制为需要一定程度的降雨。
    - 例如可以使用 `.rain(1.0)`，让配方需要满级降雨。
- Adjacent Fluids: `.adjacentFluids("namespace:fluid_id", ...)`
    - 可以向数组传入任意数量的 fluids。此外，数组中传入的任何 fluid 都会要求机器接触一个完整 source block。
    - 例如可以使用 `.adjacentFluids("minecraft:water", "minecraft:lava")`，让配方同时要求机器旁边有 water source 和 lava source。
    - 我们也有 `.adjacentFluidTag("forge:water", "forge:lava")`，效果相同，但允许使用 fluid _tags_。
- Adjacent Blocks: `.adjacentBlocks("namespace:block_id", ...)`
    - 与 fluid condition 类似，可以传入 blocks，让配方要求机器接触这些 blocks。
    - 例如可以使用 `.adjacentBlocks("minecraft:stone", "minecraft:iron_block")`，让配方需要一个 Stone block 和一个 Block of Iron。
    - 我们也有 `.adjacentBlockTag("forge:stone", "forge:storage_blocks/iron")`，效果相同，但允许使用 block _tags_。
- Thunder: `.thunder(float level)`
    - 将配方限制为需要一定程度的雷暴。
    - 例如可以使用 `.thunder(1.0)`，让配方需要强雷暴。
- Vent: 此 condition 会自动添加到所有在 single block steam machine 中运行的配方上。如果机器 vent 被阻挡，它会阻止配方运行。
- Cleanroom: `.cleanroom(CleanroomType.CLEANROOM)`
    - 将配方限制为必须位于 cleanroom 内。你也可以使用 `STERILE_CLEANROOM`，以及自己的自定义 cleanroom type。
- Fusion Start EU: `.fusionStartEU(long eu)`
    - 将配方限制为 fusion machine 中必须存有指定数量的电力。要使用它，机器必须使用 FusionReactorMachine 类。
    - 例如可以使用 `.fusionStartEU(600000)`
- Station Research: `.stationResearch(b => b.researchStack("namespace:item_id").EUt(long eu).CWUt(int minCWUPerTick, int TotalCWU))`
    - 将配方限制为必须拥有特定 research stack。为了正确显示此 condition，你需要使用带 research ui component 的基础 machine recipe type，或自行制作。
    - 例如可以使用 `.stationResearch(b => b.researchStack("gtceu:lv_motor").EUt(131000).CWUt(24, 12000))`，这会让配方需要带 lv motor research 的 data orb，也会为你生成一个 research station 配方。
- Scanner Research: `.scannerResearch(b => b.researchStack("namespace:item_id").EUt(long eu))`
    - 与 station research 类似，此 condition 会将配方限制为需要 research stack。不过在这种情况下，它默认使用 data stick。
    - 例如可以使用 `.scannerResearch(b => b.researchStack("gtceu:lv_motor").EUt(8192))`，这会让配方需要带 lv motor research 的 data stick，并生成 scanner 配方。
- Environmental Hazard: `.environmentalHazard("medical_condition_name")`
    - 将配方限制为需要特定 environmental hazard 才能运行。目前，默认世界中只添加了 `"carbon_monoxide_poisoning"`。使用该 condition 的机器示例是 air scrubber。
    - 例如可以使用 `.environmentalHazard("carcinogen")`（如果你有会产生 radiation 的东西；否则该配方永远不会运行）。
- Daytime: `.daytime(boolean isNight)`
    - 根据当前是白天还是夜晚限制配方。
    - 例如可以使用 `.daytime(true)`，让配方要求夜晚才能运行。

### 依赖 Mod 的条件
- FTB Quests: `.ftbQuest("quest_id")`
    - 将配方限制为机器所有者必须完成 FTB Quests 中的某个 quest。
    - 由于每个 quest book 都不同，这里很难给出示例。
- Game Stages: `.gameStage("gamestage_id")`
    - 将配方限制为需要特定 game stage。
- Odyssey Quests (Heracles): `.heraclesQuest("quest_id")`
    - 将配方限制为机器所有者必须完成 Heracles 中的某个 quest。
    - 由于每个 quest book 都不同，这里很难给出示例。
