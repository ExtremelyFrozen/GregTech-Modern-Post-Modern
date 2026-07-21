package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.computation.ComputationConsumer;
import com.gregtechceu.gtceu.api.computation.ComputationPort;
import com.gregtechceu.gtceu.api.computation.ComputationPortPolicy;
import com.gregtechceu.gtceu.api.computation.ComputationProducer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.computation.ComputationNetworkManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class ComputationPortTrait extends MachineTrait implements ComputationPort, Comparable<ComputationPortTrait> {

    public static final MachineTraitType<ComputationPortTrait> TYPE = new MachineTraitType<>(
            ComputationPortTrait.class);

    private final ComputationPortPolicy policy;
    private final @Nullable ComputationProducer producer;
    private final @Nullable ComputationConsumer consumer;

    public ComputationPortTrait(MetaMachine machine, ComputationPortPolicy policy) {
        this(machine, policy, null, null);
    }

    public ComputationPortTrait(MetaMachine machine, ComputationPortPolicy policy,
                                @Nullable ComputationProducer producer, @Nullable ComputationConsumer consumer) {
        super(machine);
        this.policy = policy;
        this.producer = producer;
        this.consumer = consumer;
    }

    @Override
    public MachineTraitType<?> getTraitType() {
        return TYPE;
    }

    @Override
    public ComputationPortPolicy getComputationPortPolicy() {
        return policy;
    }

    @Override
    public Optional<ComputationProducer> getComputationProducer() {
        return Optional.ofNullable(producer);
    }

    @Override
    public Optional<ComputationConsumer> getComputationConsumer() {
        return Optional.ofNullable(consumer);
    }

    public BlockPos getPortPos() {
        return getBlockPos();
    }

    @Override
    public void onOpticalRouteChanged() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            ComputationNetworkManager.get(serverLevel).markPortTopologyDirty(this);
        }
    }

    @Override
    public void onMachineLoad() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            ComputationNetworkManager.get(serverLevel).registerPort(this);
        }
    }

    @Override
    public void onMachineUnload() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            ComputationNetworkManager.get(serverLevel).unregisterPort(this);
        }
    }

    @Override
    public int compareTo(@NotNull ComputationPortTrait other) {
        return Long.compare(getBlockPos().asLong(), other.getBlockPos().asLong());
    }
}
