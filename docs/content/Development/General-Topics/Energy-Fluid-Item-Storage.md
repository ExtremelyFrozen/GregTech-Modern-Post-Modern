---
title: Item/Fluid/Energy 存储
---


# 如何添加 Item Inventory / Fluid Storage / Energy Container

!!! note

    通常，这些容器应创建为 `final` 字段，这是 [Data Sync/Save 系统](../Data-Sync-System/index.md) 所需要的。
    在构造函数中设置它们的基础参数，也可以将参数传给子类修改。


## 用于 Recipe Processing 和添加 Capability 的实现

可以通过以下类创建这些容器：

- `NotifiableItemStackHandler`
- `NotifiableFluidTank`
- `NotifiableEnergyContainer`

通常，只要条件允许，应优先选择这些类而不是其他实现，因为它们会在内部变化时通知所有 listener，以提升性能。

**IO 构造参数：**

- `handlerIO`：在 recipe processing 期间，该容器被视为 input 还是 output
- `capabilityIO`：玩家是否可以使用 hopper、pipe、cable 等与该存储交互


## 通用实现

如果不需要将存储用于 recipe processing，也不需要提供 capability，可以直接使用以下更轻量的类：

- `ItemStackTransfer`
- `FluidStorage`


## 自定义实现

某些情况下，你可能需要为这些容器创建自定义实现。
请使用以下接口：

- `IItemTransfer`
- `IFluidTransfer`
- `IEnergyContainer`


## 专用 proxy 实现

如果容器存在特殊需求，可以将这些实现与一个或多个常规容器组合使用。
它们通常作为底层容器的 proxy，同时处理这些额外需求。


### 代理多个容器

- `ItemTransferList`
- `FluidTransferList`
- `EnergyContainerList`


### 指定 IO 的容器 proxy

用于代理多个容器，但限制在特定 IO 方向。

- `IOItemTransferList`
- `IOFluidTransferList`


### 限速 proxy

!!! warning inline end "尚未合并<br>_Branch: `mi-ender-link`_"

如果需要代理 item 或 fluid 容器，并限制其插入和抽取速率，可以使用以下类：

- `LimitingItemTransferProxy`
- `LimitingFluidTransferProxy`

构造参数传入的 transfer limit 不会自动刷新。因此，一旦达到该限制，容器就会停止传输任何内容。

如果希望它表现为速率限制，需要调度一个任务，定期将 transfer limit 重置为该任务间隔内的最大值：

??? example "用法示例"

    ```java
    public class MyCover extends CoverBehavior {
        private LimitingFluidTransferProxy transferProxy;
        private ConditionalSubscriptionHandler rateLimitSubscription;

        public MyCover(IFluidTransfer myFluidTransfer) {
            super(/* ... */);

            transferProxy = new LimitingFluidTransferProxy(
                    myFluidTransfer,
                    0L // Initial limit of 0, will be updated regularly in isRateLimitRefreshActive()
            );
            rateLimitSubscription = new ConditionalSubscriptionHandler(
                    this,
                    this::resetTransferRateLimit,
                    this::isRateLimitRefreshActive
            );
        }

        @Override
        public void onLoad() {
            super.onLoad();
            rateLimitSubscription.initialize(coverHolder.getLevel());
        }

        private void resetTransferRateLimit() {
            if (transferProxy == null)
                return;

            transferProxy.setRemainingTransfer(transferRate.getMilliBuckets() * 20);
        }

        private boolean isRateLimitRefreshActive() {
            // ...
        }
    }
    ```
