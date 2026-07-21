package com.gregtechceu.gtceu.common.computation;

import com.gregtechceu.gtceu.api.computation.ComputationConsumer;
import com.gregtechceu.gtceu.api.computation.ComputationPort;
import com.gregtechceu.gtceu.api.computation.ComputationProducer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComputationSolver {

    public Result solve(List<? extends ComputationPort> nodes) {
        List<ProducerEntry> producers = new ArrayList<>();
        List<ConsumerEntry> consumers = new ArrayList<>();

        for (ComputationPort node : nodes) {
            node.getComputationProducer().ifPresent(producer -> producers.add(new ProducerEntry(producer,
                    Math.max(0, producer.getOfferedCWUt()))));
            node.getComputationConsumer().ifPresent(consumer -> {
                int minimum = Math.max(0, consumer.getMinimumCWUt());
                int requested = Math.max(minimum, consumer.getRequestedCWUt());
                consumers.add(new ConsumerEntry(consumer, minimum, requested));
            });
        }

        int totalOffered = producers.stream().mapToInt(ProducerEntry::remaining).sum();
        int remainingOffer = totalOffered;
        for (ConsumerEntry consumer : consumers) {
            int received = Math.min(remainingOffer, consumer.minimum);
            consumer.received = received;
            remainingOffer -= received;
        }

        if (remainingOffer > 0) {
            for (ConsumerEntry consumer : consumers) {
                int extraRequest = consumer.requested - consumer.received;
                if (extraRequest <= 0) continue;
                int extra = Math.min(remainingOffer, extraRequest);
                consumer.received += extra;
                remainingOffer -= extra;
                if (remainingOffer <= 0) break;
            }
        }

        int totalReceived = 0;
        for (ConsumerEntry consumer : consumers) {
            consumer.consumer.applyReceivedCWUt(consumer.received);
            totalReceived += consumer.received;
        }

        int remainingToAssign = totalReceived;
        for (ProducerEntry producer : producers) {
            int allocated = Math.min(remainingToAssign, producer.remaining);
            producer.producer.applyProducedCWUt(allocated);
            remainingToAssign -= allocated;
        }

        Map<ComputationConsumer, Integer> allocations = new HashMap<>();
        for (ConsumerEntry consumer : consumers) {
            allocations.put(consumer.consumer, consumer.received);
        }
        return new Result(totalOffered, totalReceived, Math.max(0, remainingOffer), allocations);
    }

    public record Result(int totalOfferedCWUt, int allocatedCWUt, int spareCWUt,
                         Map<ComputationConsumer, Integer> allocations) {}

    private record ProducerEntry(ComputationProducer producer, int remaining) {}

    private static class ConsumerEntry {

        private final ComputationConsumer consumer;
        private final int minimum;
        private final int requested;
        private int received;

        private ConsumerEntry(ComputationConsumer consumer, int minimum, int requested) {
            this.consumer = consumer;
            this.minimum = minimum;
            this.requested = requested;
        }
    }
}
