---
title: Central Monitor 与 Placeholder 系统
---

### 自定义 monitor module
如果想添加 monitor module，只需向你的 `ComponentItem` 附加一个实现 `IMonitorModuleItem` 的 component。
Module 可以拥有自定义 UI，可以被 tick（在 placeholder 中或不在其中），最重要的是可以被渲染。
??? example "Java 中的自定义 module 示例"
    ```java
    public class ExampleModuleBehaviour implements IMonitorModuleItem {
        @Override
        public String getType() {
            // can be any string, this is currently only used for CC: Tweaked compat
            return "example";
        }

        @Override
        public void tick(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
            // this is only called on the logical server
            // put all of your module's logic here instead of in getRenderer(stack)
            // can also be left completely empty (like in the image module)
        }

        @Override
        public void tickInPlaceholder(ItemStack stack, PlaceholderContext context) {
            // this is also only called on the logical server, but only when a placeholder accesses this module and wants to render it
            // this *isn't* called on each tick
            // you can even put the same code here as in the tick() method, like the text module does
        }

        @Override
        public IMonitorRenderer getRenderer(ItemStack stack) {
            // this is only called on the logical client
            // should return a new instance of the renderer for this module (not null)
            // for examples of renderer code look in the GTCEu Modern github:
            // https://github.com/GregTechCEu/GregTech-Modern/tree/1.20.1/src/main/java/com/gregtechceu/gtceu/client/renderer
            return new MonitorTextRenderer(MultiLineComponent.of("this text is displayed on the monitor"), 1.0);
        }

        @Override
        public Widget createUIWidget(ItemStack stack, CentralMonitorMachine machine, MonitorGroup group) {
            // should create the UI for your module and return it
            // if the module doesn't need a UI just return new WidgetGroup()
            return new WidgetGroup();
        }
    }
    ```

!!! info "关于 placeholder 系统本身的信息，请见 [gameplay wiki 页面](../../Gameplay/Central-Monitor.md)"

### 添加自定义 placeholder

可以在运行时任何时候调用 `PlaceholderHandler.addPlaceholder(...)` 添加 placeholder（最好在 mod 初始化时）。
它们可以接收任意数量的参数，形式为 `List<MultiLineComponent>`。它们还会接收一个 `PlaceholderContext` 实例，
并且必须返回 `MultiLineComponent`。Placeholder 也可以通过 `MultiLineComponent.addRenderer()`、`GraphicsComponent`
和 `IPlaceholderRenderer` 渲染几乎任何内容，而不仅是文本（`IPlaceholderRenderer` 必须使用 `PlaceholderHandler.addRenderer(...)` 单独注册）。

??? example "Java 中的 `sum` placeholder 示例"
    ```java
    public class Example {
        // you should call this function at mod initialization
        public static void addPlaceholders() {
            int priority = 1; // by default the priority of all placeholders is 0 (you don't have to specify it)
            PlaceholderHandler.addPlaceholder(new Placeholder("sum", priority) {
                @Override
                public MultiLineComponent apply(PlaceholderContext ctx, List<MultiLineComponent> args) throws PlaceholderException {
                    PlaceholderUtils.checkArgs(args, 2); // check that there are exactly 2 arguments
                    double a = PlaceholderUtils.toDouble(args.get(0));
                    double b = PlaceholderUtils.toDouble(args.get(1));
                    return MultiLineComponent.literal(a + b);
                }
            });
            // you can call addPlaceholder as many times as you need
            // if you want to override an existing placeholder, simply add a new one with the same name and a higher or equal priority
        }
    }
    ```

!!! tip "Placeholder 异常"
    处理 placeholder 时发生的任何 runtime exception 都会被捕获，甚至会显示给玩家。
    不过，与其依赖 runtime exception，你应当抛出 `PlaceholderException` 的任意子类，例如
    `InvalidNumberException` 或 `MissingItemException`。所有 `PlaceholderUtils` 方法都会抛出这些异常，因此例如应该使用它们，
    而不是自行调用 `parseDouble`。

!!! note "Placeholder 数据"
    如果你的 placeholder 需要保存特定于 placeholder 调用方的数据，可以在 placeholder 中的任何位置使用 `getData(ctx)`。
    它会返回一个自动保存的 `CompoundTag`，你可以按任意方式修改它。

### Placeholder 图形

你可能已经注意到，有些 placeholder 会输出图形而不是文本，例如 `rect` 或 `quad`。
要实现这一点，你需要编写自己的类来实现 `IPlaceholderRenderer`，或者使用现有实现。
它们的工作方式类似普通 renderer，但你可以从 placeholder 向它们传入一个 `CompoundTag`。
要注册 renderer，请调用 `PlaceholderHandler.addRenderer("put_id_here", new YourRendererClassHere())`。
之后，你可以在 placeholder 将要返回的对象上调用 `output.addGraphics(new GraphicsComponent(x, y, "put_id_here", renderData)`，
从任意 placeholder 引用该 renderer。`renderData` 就是会作为参数传入 renderer 的同一个 `CompoundTag`。
这样做是为了避免在服务端调用渲染代码，因为所有 placeholder 都只在服务端处理。这样还有一个很实用的副作用：
所有玩家在 monitor 上看到的内容（几乎总是）相同。

!!! warning "图形不适用于 Computer Monitor Cover"

### Placeholder 解析

你可能想添加某些需要解析包含 placeholder 的字符串的功能。要实现这一点，可以使用
`PlaceholderHandler.processPlaceholders(string, context)`。也可以使用 `PlaceholderHandler.placeholderExists(name)`
检查 placeholder 是否存在，或使用 `PlaceholderHandler.getAllPlaceholderNames()` 获取所有 placeholder。
要获得 `PlaceholderContext`，只需调用它的 constructor（它接收 `Level`、`BlockPos` 等基础参数，其中大多数可以为 `null`）。
