package com.gregtechceu.gtceu.common.computation;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.computation.ComputationConsumer;
import com.gregtechceu.gtceu.api.computation.ComputationPort;
import com.gregtechceu.gtceu.api.computation.ComputationPortPolicy;
import com.gregtechceu.gtceu.api.computation.ComputationProducer;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;
import java.util.Optional;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ComputationSolverTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputationSolver")
    public static void distributesMinimumBeforeRequestedComputation(GameTestHelper helper) {
        TestProducer producer = new TestProducer(20);
        TestConsumer first = new TestConsumer(8, 12);
        TestConsumer second = new TestConsumer(6, 20);

        ComputationSolver.Result result = new ComputationSolver().solve(List.of(
                TestPort.producer(producer),
                TestPort.consumer(first),
                TestPort.consumer(second)));

        helper.assertTrue(result.totalOfferedCWUt() == 20, "wrong total offered CWU/t");
        helper.assertTrue(result.allocatedCWUt() == 20, "wrong allocated CWU/t");
        helper.assertTrue(result.spareCWUt() == 0, "wrong spare CWU/t");
        helper.assertTrue(first.receivedCWUt == 12, "first consumer did not receive requested CWU/t");
        helper.assertTrue(second.receivedCWUt == 8, "second consumer did not receive remaining CWU/t");
        helper.assertTrue(producer.allocatedCWUt == 20, "producer did not receive allocated CWU/t");
        helper.assertTrue(result.allocations().get(first) == 12, "first allocation missing from result");
        helper.assertTrue(result.allocations().get(second) == 8, "second allocation missing from result");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputationSolver")
    public static void reportsSpareComputationAfterRequestsAreSatisfied(GameTestHelper helper) {
        TestProducer producer = new TestProducer(40);
        TestConsumer first = new TestConsumer(8, 12);
        TestConsumer second = new TestConsumer(6, 20);

        ComputationSolver.Result result = new ComputationSolver().solve(List.of(
                TestPort.producer(producer),
                TestPort.consumer(first),
                TestPort.consumer(second)));

        helper.assertTrue(result.allocatedCWUt() == 32, "wrong allocated CWU/t");
        helper.assertTrue(result.spareCWUt() == 8, "wrong spare CWU/t");
        helper.assertTrue(first.receivedCWUt == 12, "first consumer did not receive requested CWU/t");
        helper.assertTrue(second.receivedCWUt == 20, "second consumer did not receive requested CWU/t");
        helper.assertTrue(producer.allocatedCWUt == 32, "producer did not receive consumed CWU/t");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputationSolver")
    public static void producerAllocationStopsAtConsumedComputation(GameTestHelper helper) {
        TestProducer firstProducer = new TestProducer(7);
        TestProducer secondProducer = new TestProducer(20);
        TestConsumer consumer = new TestConsumer(10, 13);

        ComputationSolver.Result result = new ComputationSolver().solve(List.of(
                TestPort.producer(firstProducer),
                TestPort.producer(secondProducer),
                TestPort.consumer(consumer)));

        helper.assertTrue(result.allocatedCWUt() == 13, "wrong allocated CWU/t");
        helper.assertTrue(firstProducer.allocatedCWUt == 7, "first producer allocation was wrong");
        helper.assertTrue(secondProducer.allocatedCWUt == 6, "second producer allocation was wrong");
        helper.assertTrue(consumer.receivedCWUt == 13, "consumer did not receive requested CWU/t");
        helper.succeed();
    }

    private record TestPort(Optional<ComputationProducer> producer, Optional<ComputationConsumer> consumer)
            implements ComputationPort {

        private static TestPort producer(ComputationProducer producer) {
            return new TestPort(Optional.of(producer), Optional.empty());
        }

        private static TestPort consumer(ComputationConsumer consumer) {
            return new TestPort(Optional.empty(), Optional.of(consumer));
        }

        @Override
        public ComputationPortPolicy getComputationPortPolicy() {
            return ComputationPortPolicy.OPTICAL_AND_ADJACENT;
        }

        @Override
        public Optional<ComputationProducer> getComputationProducer() {
            return producer;
        }

        @Override
        public Optional<ComputationConsumer> getComputationConsumer() {
            return consumer;
        }
    }

    private static class TestProducer implements ComputationProducer {

        private final int offeredCWUt;
        private int allocatedCWUt;

        private TestProducer(int offeredCWUt) {
            this.offeredCWUt = offeredCWUt;
        }

        @Override
        public int getOfferedCWUt() {
            return offeredCWUt;
        }

        @Override
        public void applyProducedCWUt(int allocatedCWUt) {
            this.allocatedCWUt = allocatedCWUt;
        }
    }

    private static class TestConsumer implements ComputationConsumer {

        private final int minimumCWUt;
        private final int requestedCWUt;
        private int receivedCWUt;

        private TestConsumer(int minimumCWUt, int requestedCWUt) {
            this.minimumCWUt = minimumCWUt;
            this.requestedCWUt = requestedCWUt;
        }

        @Override
        public int getMinimumCWUt() {
            return minimumCWUt;
        }

        @Override
        public int getRequestedCWUt() {
            return requestedCWUt;
        }

        @Override
        public void applyReceivedCWUt(int receivedCWUt) {
            this.receivedCWUt = receivedCWUt;
        }
    }
}
