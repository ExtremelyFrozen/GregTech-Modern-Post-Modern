---
title: 创建 Connected Textures
---

**Connected textures** 顾名思义，是会与相邻 Block 连接的纹理。

CTM renderer 会通过从 5 张可用的 Block 纹理中组装 4 个象限来绘制 Block 面。
普通的 `texture.png` 是 Block 的“未连接”纹理，会在 CTM 被禁用或该 Block
没有可连接对象时使用。
`texture.png` 包含外角象限，`texture_ctm.png` 包含连接部分。
```
┌─────────────────┐ ┌────────────────────────────────┐
│ texture.png     │ │ texture_ctm.png                │
│ ╔══════╤══════╗ │ │  ──────┼────── ║ ─────┼───── ║ │
│ ║      │      ║ │ │ │      │      │║      │      ║ │
│ ║ 4/4  │ 4/5  ║ │ │ │ 0/0  │ 0/1  │║ 0/2  │ 0/3  ║ │
│ ╟──────┼──────╢ │ │ ┼──────┼──────┼╟──────┼──────╢ │
│ ║      │      ║ │ │ │      │      │║      │      ║ │
│ ║ 5/4  │ 5/5  ║ │ │ │ 1/0  │ 1/1  │║ 1/2  │ 1/3  ║ │
│ ╚══════╧══════╝ │ │  ──────┼────── ║ ─────┼───── ║ │
└─────────────────┘ │ ═══════╤═══════╝ ─────┼───── ╚ │
                    │ │      │      ││      │      │ │
                    │ │ 2/0  │ 2/1  ││ 2/2  │ 2/3  │ │
                    │ ┼──────┼──────┼┼──────┼──────┼ │
                    │ │      │      ││      │      │ │
                    │ │ 3/0  │ 3/1  ││ 3/2  │ 3/3  │ │
                    │ ═══════╧═══════╗ ─────┼───── ╔ │
                    └────────────────────────────────┘
```

例如，组合 4/4、2/1、5/4 和 3/1 这些区域，就能生成一个向右连接的纹理！
```
╔══════╤═══════
║      │      │
║ 4/4  │ 2/1  │
╟──────┼──────┼
║      │      │
║ 5/4  │ 3/1  │
╚══════╧═══════
```
组合 0/2、2/3、5/4 和 3/1 这些区域，就能生成一个 L 形纹理（向右和向上连接）：
```
║ ─────┼───── ╚
║      │      │
║ 0/2  │ 2/3  │
╟──────┼──────┼
║      │      │
║ 5/4  │ 3/1  │
╚══════╧═══════
```


??? example "MCMeta 文件示例"
    （对于纹理 `mypack/assets/textures/blocks/texture.png` 及其 ctm 纹理 `mypack/assets/textures/blocks/texture_ctm.png`）
    ```json title="mypack:blocks/texture.png.mcmeta"
    {
        "gtpm": {
            "connection_texture": "mypack:blocks/texture_ctm"
        }
    }
    ```
    未连接纹理的 CTM texture layout 在 [这里](https://github.com/ExtremelyFrozen/GregTech-Post-Modern/blob/1.21/src/main/resources/assets/gtceu/textures/block/ctm_test.png)，其连接纹理的布局在 [这里](https://github.com/ExtremelyFrozen/GregTech-Post-Modern/blob/1.21/src/main/resources/assets/gtceu/textures/block/ctm_test_ctm.png)。
    对应的 MCMeta metadata 文件是 [这个](https://github.com/ExtremelyFrozen/GregTech-Post-Modern/blob/1.21/src/main/resources/assets/gtceu/textures/block/ctm_test.png.mcmeta)。
