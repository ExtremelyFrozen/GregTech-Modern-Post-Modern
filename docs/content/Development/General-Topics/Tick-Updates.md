---
title: Tick 更新
---


# 如何使用 `ITickable` / `update()`

client update 始终存在，你可以 override `clientTick()` 方法，它的用法与 1.12 中一样。

但出于性能考虑，我们的 machine 不再始终处于 tickable 状态。
我们引入了 `ITickSubscription` 来管理 tick 逻辑。
基本思路是：只在需要周期更新时订阅它，不再需要时取消订阅。


## 实现示例

machine 的自动输出需要周期性地将内部 item 输出到相邻 inventory。
但在大多数情况下，只要满足以下任一条件，这段逻辑就不需要执行：

- machine 内没有 item
- 自动输出没有启用
- 没有可接收该 item 的相邻方块

下面看 `QuantumChest` 中的实现方式。


??? example "`QuantumChest` 中的实现"

    ```java
    @Getter @Persisted @DescSynced
    protected boolean autoOutputItems;
    @Persisted @DropSaved
    protected final NotifiableItemStackHandler cache; // inner inventory
    protected TickableSubscription autoOutputSubs;
    protected ISubscription exportItemSubs;

    // update subscription, subscribe if tick logic subscription is required, unsubscribe otherwise.
    protected void updateAutoOutputSubscription() {
        var outputFacing = getOutputFacingItems(); // get output facing
        if ((isAutoOutputItems() && !cache.isEmpty()) // inner item non empty
                && outputFacing != null // has output facing
                && ItemTransferHelper.getItemTransfer(getLevel(), getPos().relative(outputFacing), outputFacing.getOpposite()) != null) { // adjacent block has inventory.
            autoOutputSubs = subscribeServerTick(autoOutputSubs, this::checkAutoOutput); // subscribe tick logic
        } else if (autoOutputSubs != null) { // unsubscribe tick logic
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    // output to nearby block.
    protected void checkAutoOutput() {
        if (getOffsetTimer() % 5 == 0) {
            if (isAutoOutputItems() && getOutputFacingItems() != null) {
                cache.exportToNearby(getOutputFacingItems());
            }
            updateAutoOutputSubscription(); // dont foget to check if it's still available
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            // you cant call ItemTransferHelper.getItemTransfer while chunk is loading, so lets defer it next tick.
            serverLevel.getServer().tell(new TickTask(0, this::updateAutoOutputSubscription));
        }
        // add a listener to listen the changes of inner inventory. (for ex, if inventory not empty anymore, we may need to unpdate logic)
        exportItemSubs = cache.addChangedListener(this::updateAutoOutputSubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload(); //autoOutputSubs will be released automatically when machine unload
        if (exportItemSubs != null) {  //we should mannually release it.
            exportItemSubs.unsubscribe();
            exportItemSubs = null;
        }
    }

    // For any change may affect the logic to invoke updateAutoOutputSubscription at a time
    @Override
    public void setAutoOutputItems(boolean allow) {
        this.autoOutputItems = allow;
        updateAutoOutputSubscription();
    }

    @Override
    public void setOutputFacingItems(Direction outputFacing) {
        this.outputFacingItems = outputFacing;
        updateAutoOutputSubscription();
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
    }
    ```

这段代码确实有点长，但这是为了性能。借助 SyncData 系统，我们已经去掉了大量同步代码，因此这里为更好的性能保留了一些订阅管理逻辑。


## 使用 `ConditionalSubscriptionHandler`

在某些场景中，可以用 `ConditionalSubscriptionHandler` 去掉一部分模板代码，并将订阅管理委托给它。

使用这个类时，只需要提供一个在订阅激活期间每 tick 执行的 update method，以及一个决定订阅是否激活的 `Supplier<Boolean>`。

只要条件的输入发生变化，就需要在 handler 上调用 `updateSubscription()`，让它重新评估条件，并在条件变化时采取必要操作。
多数情况下，也应该在执行完 tick 逻辑后调用这个方法，确保订阅不会在不需要时继续保持激活。

??? example "使用 `ConditionalSubscriptionHandler` 的示例"

    ```java
    class MyMachine extends MetaMachine implements IControllable {
        @Persisted @Getter
        private boolean workingEnabled = true;

        private final ConditionalSubscriptionHandler subscriptionHandler;

        public MyMachine() {
            super(/* ... */);

            this.subscriptionHandler = new ConditionalSubscriptionHandler(
                this, this::update, this::isSubscriptionActive
            );
        }

        private void update() {
            // Only run once every second
            if (getOffsetTimer() % 20 != 0)
                return;

            // ...

            // Now that the update logic has been executed, update the subscription.
            // This will internally check if the subscription is still active and
            // unsubscribe otherwise.
            subscriptionHandler.updateSubscription();
        }

        private boolean isSubscriptionActive() {
            return isWorkingEnabled();
        }

        @Override
        public void setWorkingEnabled(boolean workingEnabled) {
            this.workingEnabled = workingEnabled;

            // Whether the subscription is currently active depends on whether working
            // is enabled for this machine. As soon as any of the condition inputs changes,
            // you need to update the subscription.
            subscriptionHandler.updateSubscription();
        }

        @Override
        public void onLoad() {
            super.onLoad();

            // As soon as you can get a reference to the dimension/level you're in,
            // you need to initialize your subscription handler.
            subscriptionHandler.initialize(getLevel());
        }
    }
    ```
