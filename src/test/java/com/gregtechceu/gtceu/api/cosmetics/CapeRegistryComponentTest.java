package com.gregtechceu.gtceu.api.cosmetics;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CapeRegistryComponentTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CapeRegistryComponent")
    public static void capeRegistryCodecRoundTripKeepsUnlockedAndCurrentCapes(GameTestHelper helper) {
        UUID owner = UUID.fromString("abcdef01-2345-6789-abcd-ef0123456789");
        ResourceLocation firstCape = GTCEu.id("test/first_cape");
        ResourceLocation secondCape = GTCEu.id("test/second_cape");
        CapeRegistry.registerCape(firstCape, GTCEu.id("textures/test/first_cape.png"));
        CapeRegistry.registerCape(secondCape, GTCEu.id("textures/test/second_cape.png"));

        CapeRegistry.clearMaps();
        CapeRegistry.unlockCape(owner, firstCape);
        CapeRegistry.unlockCape(owner, secondCape);
        CapeRegistry.giveRawCape(owner, secondCape);

        DataComponentMap components = CapeRegistry.exportComponents();
        JsonElement json = DataComponentMap.CODEC
                .encodeStart(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), components)
                .getOrThrow(GameTestAssertException::new);
        DataComponentMap decodedComponents = DataComponentMap.CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()), json)
                .getOrThrow(GameTestAssertException::new);

        CapeRegistry.clearMaps();
        CapeRegistry.importComponents(decodedComponents);

        helper.assertTrue(components.has(GTDataComponents.CAPE_REGISTRY.get()),
                "cape registry did not export root component");
        helper.assertTrue(CapeRegistry.getUnlockedCapes(owner).contains(firstCape),
                "first unlocked cape did not round-trip");
        helper.assertTrue(CapeRegistry.getUnlockedCapes(owner).contains(secondCape),
                "second unlocked cape did not round-trip");
        helper.assertTrue(secondCape.equals(CapeRegistry.getPlayerCapeId(owner)),
                "current cape did not round-trip");
        CapeRegistry.clearMaps();
        helper.succeed();
    }
}
