---
title: 全局缓存 / 数据
---


# 全局存储数据

在某些情况下，例如用于保存当前已加载的所有 machine 实例的缓存，你可能需要把数据存入全局的 static mutable 变量中。

这样做时，必须确保 remote 实例和 server side 实例不会混在一起。


## 使用 `SideLocal<T>`

!!! warning inline end "尚未合并<br>_Branch: `mi-ender-link`_"

为了更容易满足这个要求，可以使用 `SideLocal<T>` 存储全局数据。
它类似 Java 的 `ThreadLocal`，但作用范围是游戏的 side。

如果当前处于 remote side，也就是 `GTCEuAPI.isClientThread()` 或 client 的 `main` thread，它会返回数据的 remote side 实例。否则会返回 server side 实例。

??? example "用法示例"

    ```java
    public class MyCache {
        private static SideLocal<Map<UUID, MyData>> cache = new SideLocal<>(HashMap::new);

        public static void cacheData(UUID id, MyData data) {
            cache.get().put(id, data);
        }

        public static MyData getData(UUID id) {
            return cache.get().get(id);
        }
    }
    ```

    除了向 `SideLocal` 构造函数传入两个实例的 initializer，也可以分别提供 remote side 和 server side 的实例。
