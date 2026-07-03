---
icon: "material/information-box"
title: "开发术语表"
search:
    boost: 100
---


<!--
    !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!  IMPORTANT  !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    !!                                                                               !!
    !!   When editing this document, always keep the entries in alphabetical order   !!
    !!                                                                               !!
    !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
-->


# :material-information-box: 开发术语表

!!! info
    这里概述了本文档中常用的技术术语。
    如果你不确定某个术语的含义，或者不确定它在当前上下文中的作用，请参考本页。


## Client Side（客户端侧） { #client-side }

运行在玩家电脑上的游戏部分。

它始终承载 [Remote Side](#remote-side)。
在单人模式中，[Server Side](#server-side) 也托管在 client 上；在多人模式中，client 会连接到专用服务器。


## Remote Side（远程侧） { #remote-side }

!!! info inline end "另请参阅：[Server Side](#server-side)"

Remote Side 是游戏中**连接到** Server Side 的部分。
它始终运行在 [client](#client-side) 上。

这一侧可用的数据量可能少于 server。如果需要自动将某些数据同步到 Remote Side，请参阅 [Data Sync/Save 系统](Data-Sync-System/index.md)。
它也不会执行任何 tick 更新逻辑。


## Server Side（服务器侧） { #server-side }

!!! info inline end "另请参阅：[Remote Side](#remote-side)"

Server Side 是一个或多个玩家连接到的游戏部分。
在单人模式中，它运行在 [client](#client-side) 上；在多人模式中，它运行在专用服务器上。

这一侧通常拥有完整的世界数据，并负责执行 tick 更新逻辑。
因此，[TPS](#tps) 影响通常只在这一侧变得相关。


## TPS（每秒 tick 数） { #tps }

即 "ticks per second" 的缩写，理想情况下应保持为 20。

有关降低性能影响的做法，请参阅 [Tick Updates](General-Topics/Tick-Updates.md) 和 [Optimization](General-Topics/Optimization.md)。
