---
Title: 使用蒸汽
---

# 使用蒸汽

生成一些 Steam 后，你需要找到使用它的地方。
这就是机器发挥作用的时候：它们会带来最初的自动化，并以更低成本处理部分配方，
同时不需要消耗工具耐久。

### Steam 机器列表

| 名称          | 配方类型             | 示例                                                       | 低压            | 高压    |
|---------------|----------------------|------------------------------------------------------------|-----------------|---------|
| Extractor     | 提取                 | Rubber Log -> Raw Rubber Pulp                              | 2 mB/t          | 4 mB/t  |
| Macerator     | 粉碎 / 矿石研磨      | Copper Ingot -> Copper Dust                                | 2 mB/t          | 4 mB/t  |
| Compressor    | 压缩                 | Iron Ingot -> Block of Iron                                | 2 mB/t          | 4 mB/t  |
| Forge Hammer  | 锻锤 / 矿石破碎      | Iron Ingot -> Iron Plate                                   | 16 mB/t         | 32 mB/t |
| Furnace       | 矿石烧炼             | Bronze Dust -> Bronze Ingot                                | 4 mB/t          | 8 mB/t  |
| Alloy Smelter | 合金冶炼 / 金属成型  | Copper 与 Tin -> Bronze Ingot                              | 16 mB/t         | 32 mB/t |
| Rock Crusher  | 破岩                 | Cobblestone, Water Source, Lava Source -> Cobblestone      | 7 mB/t          | 14 mB/t |
| Miner         | 自动采矿             | 开采 9x9 区域内的方块，并生成矿石                          | 16 mB/t         | 32 mB/t |
| Steam Grinder | 粉碎 / 矿石研磨      | 与 Macerator 相同，但最多可同时处理八个配方                | 每配方 2 mB/t   | -       |
| Steam Oven    | 矿石烧炼             | 与 Furnace 相同，但最多可同时处理八个配方                  | 每配方 6 mB/t   | -       |

!!! info "特殊单方块机器"

   Rock Crusher（破岩机）和 Miner（采矿机）是有额外要求的特殊机器。

   * Rock Crusher 需要旁边有 Water Source（水源）和 Lava Source（岩浆源）。
      ![Rock Crusher 布置](./assets/rock_crusher_setup.png)
   * Miner 需要工作范围内、机器下方存在矿石方块。
