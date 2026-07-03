---
title: "Ore Generation（矿石生成）"
---


# Ore Generation（矿石生成）

GregTech 拥有自己的 Ore Generation 系统，它与 Minecraft 标准系统有很大不同。
由于技术限制，此系统不使用 Minecraft 的 features，工作方式也略有不同。

GT 的矿石会以大型 Ore Vein 的形式生成，这些矿脉会沿着遍布世界的网格放置（每个矿脉还有随机偏移）。

在本节中，你将学习如何为整合包自定义 Ore Generation。


## 重新加载 Ore Generation

!!! warning
    更改 Ore Generation 后，你必须**重启服务器**或**重新打开世界**！
    在这种情况下，仅使用 `/reload` 是不够的。
