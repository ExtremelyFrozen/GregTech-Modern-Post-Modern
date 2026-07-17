package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.editor.MachineUIXmlTemplates;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class EditableMachineUIResourceTest {

    // spotless:off
    private static final String CUSTOM_MACHINE_XML = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <ldlib2-ui xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:noNamespaceSchemaLocation="https://raw.githubusercontent.com/Low-Drag-MC/LDLib2/refs/heads/1.21/ldlib2-ui.xsd">
                <stylesheet location="ldlib2:lss/mc.lss"/>
                <root style="position: relative; width: 120; height: 78;">
                    <element id="recipe_template"
                            style="position: absolute; left: 14; top: 26; width: 92; height: 26;">
                        <gtm-item-slot id="item_in_0"
                                style="position: absolute; left: 4; top: 4; width: 18; height: 18;"
                                draw-hover-overlay="true" draw-hover-tips="true"
                                can-put-items="true" can-take-items="true"
                                legacy-background="border_texture:gtpm:textures/gui/base/slot.png"/>
                        <gtm-progress-bar id="progress"
                                style="position: absolute; left: 36; top: 3; width: 20; height: 20;"
                                fill-direction="LEFT_TO_RIGHT"/>
                    </element>
                    <gtm-item-slot id="battery_slot"
                            style="position: absolute; left: 51; top: 60; width: 18; height: 18;"
                            draw-hover-overlay="true" draw-hover-tips="true"
                            can-put-items="true" can-take-items="true"
                            legacy-background="border_texture:gtpm:textures/gui/base/slot.png"
                            legacy-overlay="resource_texture:gtpm:textures/gui/overlay/charger_slot_overlay.png"/>
                </root>
            </ldlib2-ui>
            """;
    // spotless:on

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EditableMachineUIResource")
    public static void clientResourcePackOverrideLoadsAndBindsMachineXml(GameTestHelper helper) {
        SimpleTieredMachine machine = (SimpleTieredMachine) TestUtils.setMachine(helper,
                new BlockPos(1, 1, 1), GTMachines.ARC_FURNACE[GTValues.LV]);
        EditableMachineUI template = machine.getDefinition().getEditableUI();
        if (template == null) {
            throw new AssertionError("arc furnace has no editable machine XML metadata");
        }

        Path packRoot = createPackRoot(template);
        var locationInfo = new PackLocationInfo("machine-ui-test", Component.literal("Machine UI Test"),
                PackSource.DEFAULT, Optional.empty());
        var pack = new PathPackResources(locationInfo, packRoot);
        try (var resourceManager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(pack))) {
            template.reloadCustomUI();
            var ui = template.createUI(resourceManager, machine);
            var size = template.getSize(resourceManager);

            helper.assertTrue(size.width() == 120 && size.height() == 78,
                    "machine XML resource override did not provide the parsed root size");
            GTItemSlotElement batterySlot = MachineUIXmlTemplates.requireElement(ui.rootElement,
                    MachineUIXmlTemplates.BATTERY_SLOT_ID, GTItemSlotElement.class);
            helper.assertTrue(batterySlot.getSlot() instanceof ItemHandlerSlot,
                    "resource machine XML battery slot was not bound as an item-handler slot");
            ItemHandlerSlot batteryHandlerSlot = (ItemHandlerSlot) batterySlot.getSlot();
            helper.assertTrue(batteryHandlerSlot.getItemHandler() == machine.getChargerInventory() &&
                    batteryHandlerSlot.getSlotIndex() == 0,
                    "resource machine XML battery slot did not bind charger slot zero");

            GTItemSlotElement recipeInput = MachineUIXmlTemplates.requireElement(ui.rootElement,
                    "item_in_0", GTItemSlotElement.class);
            helper.assertTrue(recipeInput.getSlot() instanceof ItemHandlerSlot,
                    "resource machine XML recipe input was not bound as an item-handler slot");
            ItemHandlerSlot recipeHandlerSlot = (ItemHandlerSlot) recipeInput.getSlot();
            helper.assertTrue(recipeHandlerSlot.getItemHandler() == machine.importItems.storage &&
                    recipeHandlerSlot.getSlotIndex() == 0,
                    "resource machine XML item_in_0 did not bind import slot zero");
        } finally {
            template.reloadCustomUI();
            deleteTree(packRoot);
        }
        helper.succeed();
    }

    private static Path createPackRoot(EditableMachineUI template) {
        try {
            Path packRoot = Files.createTempDirectory("gt-machine-ui-pack-");
            Path xmlPath = packRoot.resolve("assets")
                    .resolve(template.getXmlLocation().getNamespace())
                    .resolve(template.getXmlLocation().getPath());
            Files.createDirectories(xmlPath.getParent());
            Files.writeString(xmlPath, CUSTOM_MACHINE_XML, StandardCharsets.UTF_8);
            return packRoot;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create the machine UI test resource pack", e);
        }
    }

    private static void deleteTree(Path root) {
        List<Path> paths;
        try (var pathStream = Files.walk(root)) {
            paths = pathStream.sorted(Collections.reverseOrder()).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to enumerate machine UI test resource pack " + root, e);
        }
        for (Path path : paths) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to clean machine UI test resource path " + path, e);
            }
        }
    }
}
