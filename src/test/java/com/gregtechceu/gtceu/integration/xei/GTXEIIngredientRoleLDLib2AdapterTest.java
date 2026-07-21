package com.gregtechceu.gtceu.integration.xei;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
@SuppressWarnings("unused")
public class GTXEIIngredientRoleLDLib2AdapterTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTXEIIngredientRoleLDLib2Adapter")
    public static void adapterMapsGTMRoleToLDLib2Role(GameTestHelper helper) {
        assertToLDLib2(helper, GTXEIIngredientRole.INPUT, IngredientIO.INPUT);
        assertToLDLib2(helper, GTXEIIngredientRole.OUTPUT, IngredientIO.OUTPUT);
        assertToLDLib2(helper, GTXEIIngredientRole.CATALYST, IngredientIO.CATALYST);
        assertToLDLib2(helper, GTXEIIngredientRole.NONE, IngredientIO.NONE);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTXEIIngredientRoleLDLib2Adapter")
    public static void adapterMapsLDLib2RoleToGTMRole(GameTestHelper helper) {
        assertFromLDLib2(helper, IngredientIO.INPUT, GTXEIIngredientRole.INPUT);
        assertFromLDLib2(helper, IngredientIO.OUTPUT, GTXEIIngredientRole.OUTPUT);
        assertFromLDLib2(helper, IngredientIO.CATALYST, GTXEIIngredientRole.CATALYST);
        assertFromLDLib2(helper, IngredientIO.NONE, GTXEIIngredientRole.NONE);
        helper.succeed();
    }

    private static void assertToLDLib2(GameTestHelper helper, GTXEIIngredientRole role, IngredientIO expected) {
        IngredientIO actual = GTXEIIngredientRoleLDLib2Adapter.toLDLib2(role);
        helper.assertTrue(actual == expected, "GTM role " + role + " did not map to LDLib2 role " + expected);
    }

    private static void assertFromLDLib2(GameTestHelper helper, IngredientIO role, GTXEIIngredientRole expected) {
        GTXEIIngredientRole actual = GTXEIIngredientRoleLDLib2Adapter.fromLDLib2(role);
        helper.assertTrue(actual == expected, "LDLib2 role " + role + " did not map to GTM role " + expected);
    }
}
