package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib2.LDLib2;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTUIXmlProjectTest {

    // spotless:off
    private static final String TEST_XML = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <ldlib2-ui>
                <root id="editor_test_root" style="width: 48; height: 24">
                    <label text="UTF-8: test é"/>
                </root>
            </ldlib2-ui>
            """;
    // spotless:on

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTUIXmlProject")
    public static void projectRoundTripUsesUtf8AndRestoresStructuredTarget(GameTestHelper helper) {
        var recipeTypes = TemplateTab.collectRecipeTypeTemplates();
        helper.assertTrue(!recipeTypes.isEmpty(), "no GT recipe type was available for the editor project test");

        var recipeType = recipeTypes.getFirst();
        GTUIXmlTarget target = GTUIXmlTarget.recipeType(recipeType.registryName);
        Path tempRoot = createTempDirectory();
        Path assetsRoot = tempRoot.resolve("assets");
        Path file = target.resolve(assetsRoot);
        Path expectedFile = assetsRoot.toAbsolutePath().normalize()
                .resolve(recipeType.registryName.getNamespace())
                .resolve("ui")
                .resolve("recipe_type")
                .resolve(recipeType.registryName.getPath() + ".xml");

        try {
            GTUIXmlProject project = new GTUIXmlProject().useTemplate(target, TEST_XML);
            helper.assertTrue(file.equals(expectedFile),
                    "recipe target did not resolve under the active assets root");
            helper.assertTrue(file.equals(GTUIXmlProjectType.TYPE.resolveDefaultSaveFile(project, assetsRoot)),
                    "recipe project did not resolve its exact default resource path");

            GTUIXmlProjectType.TYPE.saveProjectToFile(project, file.toFile());
            helper.assertTrue(GTUIXmlProjectType.isWithinAssetsRoot(file, assetsRoot),
                    "file under the supplied assets root was classified as external");
            helper.assertTrue(!GTUIXmlProjectType.isWithinAssetsRoot(
                    tempRoot.resolve("external-assets/example/ui/recipe_type/test.xml"), assetsRoot),
                    "file outside the supplied assets root was classified as active");
            helper.assertTrue(TEST_XML.equals(Files.readString(file, StandardCharsets.UTF_8)),
                    "saved XML did not round-trip as UTF-8");
            helper.assertTrue(!GTUIXmlProjectType.TYPE.isProjectDirty(project, file.toFile()),
                    "freshly saved XML project was dirty");

            GTUIXmlProject loaded = GTUIXmlProjectType.TYPE.loadProjectFromFile(file.toFile());
            helper.assertTrue(loaded.getTarget().orElseThrow().equals(target),
                    "loaded XML project did not infer its recipe target from the resource path");
            helper.assertTrue("editor_test_root".equals(loaded.createPreview().rootElement.getId()),
                    "UI.of(Document) did not parse the loaded XML root");

            loaded.setXml(TEST_XML.replace("width: 48", "width: 49"), false);
            helper.assertTrue(GTUIXmlProjectType.TYPE.isProjectDirty(loaded, file.toFile()),
                    "changed XML project was not dirty");
        } catch (Exception e) {
            throw new AssertionError("GT XML project round-trip failed", e);
        } finally {
            deleteTree(tempRoot);
        }

        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTUIXmlProject")
    public static void machineAndRecipeTemplateSelectionCreatesParseableProjects(GameTestHelper helper) {
        var machineTemplates = TemplateTab.collectMachineTemplates();
        helper.assertTrue(!machineTemplates.isEmpty(), "no editable machine template was registered");

        var descriptors = Collections.newSetFromMap(new IdentityHashMap<EditableMachineUI, Boolean>());
        for (var template : machineTemplates) {
            helper.assertTrue(descriptors.add(template.editableUI()),
                    "machine template collector returned the same descriptor more than once");
        }

        var machineTemplate = machineTemplates.getFirst();
        GTUIXmlProject machineProject = TemplateTab.createMachineProject(machineTemplate);
        helper.assertTrue(machineProject.getTarget().orElseThrow().equals(
                GTUIXmlTarget.machine(machineTemplate.editableUI().getUiPath())),
                "machine template selection used the wrong target");

        var recipeTypes = TemplateTab.collectRecipeTypeTemplates();
        helper.assertTrue(!recipeTypes.isEmpty(), "no GT recipe template was registered");
        var recipeType = recipeTypes.getFirst();
        GTUIXmlProject recipeProject = TemplateTab.createRecipeTypeProject(recipeType);
        helper.assertTrue(recipeProject.getTarget().orElseThrow().equals(
                GTUIXmlTarget.recipeType(recipeType.registryName)),
                "recipe template selection used the wrong target");

        Path assetsRoot = Path.of("build", "editor-default-path-test", "assets").toAbsolutePath();
        helper.assertTrue(GTUIXmlProjectType.TYPE.resolveDefaultSaveFile(machineProject, assetsRoot).equals(
                GTUIXmlTarget.machine(machineTemplate.editableUI().getUiPath()).resolve(assetsRoot)),
                "machine template selection used the wrong default save path");
        helper.assertTrue(GTUIXmlProjectType.TYPE.resolveDefaultSaveFile(recipeProject, assetsRoot).equals(
                GTUIXmlTarget.recipeType(recipeType.registryName).resolve(assetsRoot)),
                "recipe template selection used the wrong default save path");
        helper.assertTrue(machineProject.getProjectType() == GTUIXmlProjectType.TYPE &&
                recipeProject.getProjectType() == GTUIXmlProjectType.TYPE,
                "machine and recipe templates did not share the single GT XML project type");

        Path activeAssetsRoot = LDLib2.getAssetsDir().toPath().toAbsolutePath().normalize();
        helper.assertTrue(GTUIEditor.getWorkspacePath().toAbsolutePath().normalize().equals(activeAssetsRoot),
                "GT XML editor workspace was not the active LDLib2 assets root");
        helper.assertTrue(GTUIXmlProjectType.TYPE.resolveDefaultSaveFile(recipeProject, activeAssetsRoot).equals(
                activeAssetsRoot.resolve(recipeType.registryName.getNamespace())
                        .resolve("ui")
                        .resolve("recipe_type")
                        .resolve(recipeType.registryName.getPath() + ".xml")),
                "recipe project default path did not resolve under the active LDLib2 assets root");

        try {
            machineProject.createPreview();
            recipeProject.createPreview();
        } catch (Exception e) {
            throw new AssertionError("editor template XML parsing failed", e);
        }

        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTUIXmlProject")
    public static void targetInferenceDistinguishesMachineAndRecipeResourcePaths(GameTestHelper helper) {
        Path root = Path.of("build", "editor-path-test").toAbsolutePath();
        GTUIXmlTarget machine = GTUIXmlTarget.infer(
                root.resolve("assets/example/ui/machine/tiered/basic.xml")).orElseThrow();
        GTUIXmlTarget recipe = GTUIXmlTarget.infer(
                root.resolve("assets/example/ui/recipe_type/assembler.xml")).orElseThrow();

        helper.assertTrue(machine.equals(GTUIXmlTarget.machine(
                ResourceLocation.fromNamespaceAndPath("example", "tiered/basic"))),
                "machine resource path inference returned the wrong target");
        helper.assertTrue(recipe.equals(GTUIXmlTarget.recipeType(
                ResourceLocation.fromNamespaceAndPath("example", "assembler"))),
                "recipe resource path inference returned the wrong target");
        helper.assertTrue(GTUIXmlTarget.infer(root.resolve("assets/example/ui/other/ignored.xml")).isEmpty(),
                "unrelated XML path was inferred as a GT editor runtime target");

        helper.succeed();
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("gt-ui-xml-project-");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create GT XML project test directory", e);
        }
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) {
            return;
        }
        List<Path> paths;
        try (var pathStream = Files.walk(root)) {
            paths = pathStream.sorted(Collections.reverseOrder()).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to enumerate GT XML project test directory " + root, e);
        }
        for (Path path : paths) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to clean GT XML project test path " + path, e);
            }
        }
    }
}
