---
title: Ore 生成
---


# Ore 生成

由于 Minecraft 的 worldgen 限制 (1)，GTCEu 的 ore vein 生成没有使用原生 worldgen feature 系统。
相反，我们使用自己的系统，将 ore vein 的生成与实际 ore 放置分离，确保 ore 只会被放置到当前正在生成的 chunk 中。
本页大致说明 ore 的生成、缓存和放置流程。
{ .annotate }

1. 在 Minecraft 中，worldgen feature 只能在以 feature origin chunk 为中心的 3x3 chunk 区域内生成。
   由于 GTCEu 引入的 vein 可能比这个范围更大，并且还会额外带有 random offset，ore 生成在某些情况下会超出允许区域，导致 server thread 卡死或死锁。


生成流程大致可以分为三个步骤：

- Vein 生成
- 已生成 vein 缓存
- Ore 放置（chunk 生成期间）

本文会自底向上介绍这些步骤，从 chunk 生成 mixin（`ChunkGeneratorMixin.gtceu$applyBiomeDecoration()`）开始。


## Chunk 生成与 Ore 放置

`ChunkGeneratorMixin` 持有对 `OrePlacer` 的引用。不要将它与 `OreBlockPlacer` 混淆；`OrePlacer` 用于将已生成 vein 的方块放入世界，并且会限制在当前正在生成的 chunk 内。


## 已生成 Vein 缓存

尝试生成 chunk 时，`OrePlacer` 会向 `OreGenCache` 查询当前 chunk 周围的 vein 列表。

周围区域的查询半径由 `oreVeinRandomOffset` 配置项和已注册的最大 vein size 共同决定。
因此，无论额外 vein 是通过 KubeJS 还是 addon 注册，或默认 vein 被修改，它都会自动兼容。

当然，ore gen cache 一次只能保存有限数量的已生成 vein，参见 `oreGenerationChunkCacheSize` 配置项。


### 随机性

由于 vein 可能在其所有 chunk 生成完成前就从缓存中移除，ore 生成必须保持**完全确定性**，这一点极其重要。

这可以确保不会生成被截断的 ore vein，也不会在 chunk 边界两侧出现形状或类型不匹配的情况。
它也会自动跨游戏重启生效，即使重启后也能保持连续性。

除了某些内部生成逻辑变更外，ore vein 只会在相关配置项发生变化后，才可能在 chunk 边界两侧产生差异。

在我们的实现中，这意味着用于 world generation 的 `RandomSource` 必须在生成每条 vein 时都是全新的，确保 vein 的类型、形状、offset、内容等不会受到之前 random generator 查询的影响。
它只由世界 seed 和 chunk position 完全决定。

对于随机 ore vein offset，我们还会将 vein 的 world generation layer 纳入 random seed。
如果未来支持每个 chunk 和 worldgen-layer 生成多条 vein，这里可能需要额外加入一个组成部分。


## Vein 生成

当 `OreGenCache` 找不到某个特定 chunk 的 vein 时，它会向 `OreGenerator` 请求该 chunk 的 `GeneratedVein` 列表。

`OreGenerator` 负责决定 vein 的类型、origin（受 `oreVeinRandomOffset` 配置项影响），并向使用的 `VeinGenerator` 实现提供合适的 randomness source。

!!! info "Vein Origin 与 Center"

    vein 的 origin 始终是它来源的 chunk，与 random offset 无关。
    vein 的实际 center **会**受到 random offset 影响，可能不在 chunk 中心，甚至可能不在同一个 chunk 内。

相关 `VeinGenerator` 实现完成 vein 形状生成后，结果会按 chunk 缓存在 `GeneratedVein` 中。


### `VeinGenerator` 与 `OreBlockPlacer`

vein generator 负责生成 vein 的实际形状。

但是，它不应尝试直接放置任何方块。相反，它的 `generate()` 方法只会返回一张以 block position 为 key、以 `OreBlockPlacer` 为 value 的 map；这些 `OreBlockPlacer` 会在 chunk 生成时负责实际向世界放置方块。
每个 `OreBlockPlacer` 应只放置一个方块，或者不放置方块。


### 在 `OreBlockPlacer` 中使用随机性

某些情况下，实际放置方块的过程需要 randomness source，例如用于决定某个方块是否会被放置。

为了在这种情况下也保持 ore 生成完全确定，建议在 vein 形状生成时使用传入的 `RandomSource` 生成一个新 seed。这个 seed 应传入为每个 block position 返回的 `OreBlockPlacer`。

在 `OreBlockPlacer` 内部，只需使用预先计算的 seed 创建新的 `RandomSource`。

??? example "在 OreBlockPlacer 中使用随机性"

    ```java
    public class MyVeinGenerator {
        public Map<BlockPos, OreBlockPlacer> generate(WorldGenLevel level, RandomSource random, GTOreDefinition entry, BlockPos origin) {
            Map<BlockPos, OreBlockPlacer> generatedBlocks = new Object2ObjectOpenHashMap<>();

            for (BlockPos pos : determineShapePositions()) {
                final var randomSeed = random.nextLong(); // Fully deterministic regardless of chunk generation order
                generatedBlocks.put(pos, (access, section) -> placeBlock(access, section, randomSeed, pos, entry));
            }

            return generatedBlocks;
        }

        private void placeBlock(BulkSectionAccess level, LevelChunkSection section, long randomSeed, BlockPos pos, GTOreDefinition entry) {
            RandomSource rand = new XoroshiroRandomSource(randomSeed);

            // ...
        }
    }
    ```
