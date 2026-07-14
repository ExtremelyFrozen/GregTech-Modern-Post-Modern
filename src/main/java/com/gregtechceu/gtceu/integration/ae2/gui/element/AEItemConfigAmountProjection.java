package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.integration.ae2.machine.AEItemConfigSnapshot;
import com.gregtechceu.gtceu.integration.ae2.machine.MEItemConfigActions;

import net.minecraft.MethodsReturnNonnullByDefault;

import appeng.api.stacks.AEItemKey;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

/** Opening-scoped amount projection shared by the ME item editor and wheel controls. */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
final class AEItemConfigAmountProjection {

    private final Supplier<AEItemConfigSnapshot> snapshotSupplier;
    private final MachineUIHolder holder;
    private final BiConsumer<MachineUIHolder, SyncActionData> actionSender;
    private final BooleanSupplier canSendAction;
    private final Map<Integer, PendingAmount> pendingAmounts = new HashMap<>();

    AEItemConfigAmountProjection(Supplier<AEItemConfigSnapshot> snapshotSupplier,
                                 MachineUIHolder holder,
                                 BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                 BooleanSupplier canSendAction) {
        this.snapshotSupplier = snapshotSupplier;
        this.holder = holder;
        this.actionSender = actionSender;
        this.canSendAction = canSendAction;
    }

    OptionalInt projectedAmount(int slot) {
        AuthoritativeAmount authoritative = authoritativeAmount(slot);
        if (authoritative == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(projectedAmount(slot, authoritative));
    }

    boolean requestAmount(int slot, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("ME item configuration amount must be positive: " + amount);
        }
        if (!canSendAction.getAsBoolean()) {
            pendingAmounts.clear();
            return false;
        }
        AuthoritativeAmount authoritative = authoritativeAmount(slot);
        if (authoritative == null || amount == projectedAmount(slot, authoritative)) {
            return false;
        }
        pendingAmounts.put(slot, new PendingAmount(authoritative.key(), amount));
        actionSender.accept(holder, MEItemConfigActions.createSetAmountAction(
                slot, authoritative.key().toStack(1), amount));
        return true;
    }

    void reconcile() {
        AEItemConfigSnapshot snapshot = snapshotSupplier.get();
        if (!canSendAction.getAsBoolean() || snapshot.stocking() || snapshot.autoPull()) {
            pendingAmounts.clear();
            return;
        }
        pendingAmounts.entrySet().removeIf(entry -> {
            var config = snapshot.slots().get(entry.getKey()).config();
            PendingAmount pending = entry.getValue();
            return config == null || !pending.expectedKey().equals(config.what()) ||
                    config.amount() == pending.desiredAmount();
        });
    }

    private int projectedAmount(int slot, AuthoritativeAmount authoritative) {
        PendingAmount pending = pendingAmounts.get(slot);
        return pending != null && pending.expectedKey().equals(authoritative.key()) ?
                pending.desiredAmount() : authoritative.amount();
    }

    private @Nullable AuthoritativeAmount authoritativeAmount(int slot) {
        AEItemConfigSnapshot snapshot = snapshotSupplier.get();
        if (snapshot.stocking() || snapshot.autoPull() || slot < 0 || slot >= snapshot.slots().size()) {
            return null;
        }
        var config = snapshot.slots().get(slot).config();
        if (config == null) {
            return null;
        }
        if (!(config.what() instanceof AEItemKey key)) {
            throw new IllegalStateException("ME item amount projection received a non-item configuration key.");
        }
        return new AuthoritativeAmount(key, Math.toIntExact(config.amount()));
    }

    /** Latest unconfirmed amount requested for one exact slot and item identity. */
    private record PendingAmount(AEItemKey expectedKey, int desiredAmount) {}

    /** Authoritative slot state used as the base for an optional pending request. */
    private record AuthoritativeAmount(AEItemKey key, int amount) {}
}
