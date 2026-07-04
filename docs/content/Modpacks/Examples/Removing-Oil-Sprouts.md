---
title: "配置 Oil Sprouts（原油苗）"
---

# 配置 Oil Sprouts（原油苗）

!!! warning "此 feature 名为 raw_oil_**sprout**，不是 raw_oil_spout。"

Oil Sprouts（原油苗）通过 Minecraft 的 "Configured Feature" 系统生成，并且可以通过标准数据包自定义。
如果你使用 KubeJS，将文件放入 `kubejs/data` 文件夹就等同于向数据包添加文件。

## 移除 Oil Sprouts

要完全禁用 Oil Sprouts，请将以下文件放入 `kubejs/data/gtceu/worldgen/configured_feature/raw_oil_sprout.json`，
或创建一个包含等效内容的数据包。这会用 `no_op` 替换原油苗，也就是一个不会做任何事的 feature。

```json title="data/gtceu/worldgen/configured_feature/raw_oil_sprout.json"
{
"type": "minecraft:no_op",
"config": {}
}
```

## 调整 Oil Sprout 放置条件

如果你只是想调整 Oil Sprouts 的稀有度，需要通过 "Placed Feature" 系统配置。复制 placed feature 文件的
[当前版本](https://github.com/ExtremelyFrozen/GregTech-Post-Modern/blob/1.21/src/generated/resources/data/gtceu/worldgen/placed_feature/raw_oil_sprout.json)
到 `kubejs/data/gtceu/worldgen/placed_feature/raw_oil_sprout.json`，然后按需修改设置。（默认文件使用 `"minecraft:rarity_filter"`，让每个区块有 1/64 的概率包含一个原油苗。）