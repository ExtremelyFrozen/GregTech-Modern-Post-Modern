package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTBoundInputElementsTest {

    private static final String BATCH = "GTBoundInputElements";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void numericInputsClampAndWriteThroughBoundState(GameTestHelper helper) {
        int[] integerValue = { 5 };
        GTIntInputElement integerInput = new GTIntInputElement(1, 2, 100, 20,
                () -> integerValue[0], value -> integerValue[0] = value)
                .setMin(1)
                .setMax(8);

        integerInput.setValue(0);
        helper.assertTrue(integerValue[0] == 1, "integer input did not clamp to its bound minimum");
        integerInput.setValue(12);
        helper.assertTrue(integerValue[0] == 8, "integer input did not clamp to its bound maximum");
        integerInput.setValue(4);
        helper.assertTrue(integerValue[0] == 4, "integer input did not write an in-range value to its owner");

        long[] longValue = { 10L };
        GTLongInputElement longInput = new GTLongInputElement(3, 4, 120, 20,
                () -> longValue[0], value -> longValue[0] = value)
                .setMin(2L)
                .setMax(20L);

        longInput.setValue(Long.MIN_VALUE);
        helper.assertTrue(longValue[0] == 2L, "long input did not clamp to its bound minimum");
        longInput.setValue(Long.MAX_VALUE);
        helper.assertTrue(longValue[0] == 20L, "long input did not clamp to its bound maximum");
        longInput.setValue(15L);
        helper.assertTrue(longValue[0] == 15L, "long input did not write an in-range value to its owner");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void stringSelectorSynchronizesUserAndOwnerSelection(GameTestHelper helper) {
        List<String> candidates = List.of("ULV", "LV", "MV");
        String[] selected = { "LV" };
        int[] changeCount = { 0 };
        GTStringSelectorElement selector = new GTStringSelectorElement(5, 6, 80, 20, candidates,
                () -> selected[0], value -> {
                    selected[0] = value;
                    changeCount[0]++;
                });

        helper.assertTrue(selector.getCandidates().equals(candidates),
                "string selector did not retain its configured candidates");
        helper.assertTrue("LV".equals(selector.getValue()),
                "string selector did not initialize from its owner");

        selector.setSelected("MV");
        helper.assertTrue(selected[0].equals("MV") && "MV".equals(selector.getValue()),
                "string selector did not write a user selection to its owner");
        helper.assertTrue(changeCount[0] == 1,
                "string selector did not notify its owner exactly once for a user selection");

        selected[0] = "ULV";
        selector.screenTick();
        helper.assertTrue("ULV".equals(selector.getValue()),
                "string selector did not refresh an externally changed owner selection");
        helper.assertTrue(changeCount[0] == 1,
                "string selector echoed an owner refresh back through its change callback");

        boolean emptyCandidatesRejected = false;
        try {
            new GTStringSelectorElement(0, 0, 40, 20, List.of(),
                    () -> selected[0], value -> selected[0] = value);
        } catch (IllegalArgumentException expected) {
            emptyCandidatesRejected = true;
        }
        helper.assertTrue(emptyCandidatesRejected,
                "string selector accepted an empty candidate contract");
        helper.succeed();
    }
}
