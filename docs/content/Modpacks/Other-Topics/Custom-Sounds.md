---
title: 自定义 Sound
---


## 创建自定义 Sound

!!! Warning "警告"
    注册自定义 sound 目前仅在 Java 中受支持，不过 sound 定义后可以在 KubeJS 脚本中使用。

要添加新的 sound，需要一个 sounds class。
该类会为 registrate 注册 sound 做准备。
下面是 custom sound 示例。

```java
import static com.examplemod.common.registry.ExampleRegistration.REGISTRATE;

public class ExampleSound {

    public static final SoundEntry MICROVERSE = REGISTRATE.sound(ExampleMod.id("microverse")).build();

    public static void init() {}
}
```

运行 datagen 前，需要先准备好 sound。要注册 sound，它必须是 .ogg 格式并位于 `assets/examplemod/sounds`。
!!! note "mono 与 stereo audio"

    音频文件应为 mono，因为 Minecraft 的 attenuation logic 只适用于单声道音频。Stereo sounds 不会随距离衰减（无论离声源多远都会以相同音量播放），只应当用于主菜单音乐等背景音轨。

创建这个类、准备 sound 并在主 mod 类中初始化它之后，需要为 sounds 设置 datagen。
它比普通 datagen 稍复杂，示例如下。

```java
@Mod.EventBusSubscriber(modid = ExampleMod.MOD_ID)
public class ExampleDataGenerators {

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        PackOutput packOutput = event.getGenerator().getPackOutput();

        if (event.includeClient()) {
            event.getGenerator().addProvider(
                    true,
                    new SoundEntryBuilder.SoundEntryProvider(packOutput, examplemod.MOD_ID));
        }
    }
}

```
