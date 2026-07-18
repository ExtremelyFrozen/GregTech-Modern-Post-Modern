package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.ProgressTexture;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.common.collect.HashBasedTable;
import org.w3c.dom.Document;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class GTProgressBarElementTest {

    private static final float EPSILON = 0.0001f;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTProgressBarElement")
    public static void defaultLabelIsEmptyAndExplicitXmlTextIsPreserved(GameTestHelper helper)
                                                                                               throws ParserConfigurationException {
        GTProgressBarElement progressBar = new GTProgressBarElement();
        helper.assertTrue(progressBar.label.getText().getString().isEmpty(),
                "progress bar exposed LDLib2's default label text");

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        var element = document.createElement("gtm-progress-bar");
        element.setAttribute("text", "Working");
        progressBar.loadXml(element);

        helper.assertTrue(progressBar.label.getText().getString().equals("Working"),
                "progress bar discarded explicit XML text");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTProgressBarElement")
    public static void progressUsesDrawAreaAndUvCroppingForEveryAxis(GameTestHelper helper) {
        GTProgressBarElement progressBar = new GTProgressBarElement();

        progressBar.setFillDirection(FillDirection.LEFT_TO_RIGHT).setProgress(0.5f);
        refreshStyle(progressBar);
        assertArea(helper, progressBar.getProgressDrawArea(10, 20, 100, 40),
                10, 20, 50, 40, 0, 0, 0.5f, 1, "left-to-right 50 percent");

        progressBar.setFillDirection(FillDirection.RIGHT_TO_LEFT).setProgress(0.5f);
        refreshStyle(progressBar);
        assertArea(helper, progressBar.getProgressDrawArea(10, 20, 100, 40),
                60, 20, 50, 40, 0.5f, 0, 0.5f, 1, "right-to-left 50 percent");

        progressBar.setFillDirection(FillDirection.DOWN_TO_UP).setProgress(0.25f);
        refreshStyle(progressBar);
        assertArea(helper, progressBar.getProgressDrawArea(10, 20, 100, 40),
                10, 50, 100, 10, 0, 0.75f, 1, 0.25f, "down-to-up 25 percent");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "GTProgressBarElement")
    public static void defaultRecipeTemplateBindsTexturesAndAdvancingProgress(GameTestHelper helper) {
        RecordingTexture emptyTexture = new RecordingTexture();
        RecordingTexture filledTexture = new RecordingTexture();
        GTRecipeTypeUI recipeUI = new GTRecipeTypeUI(GTRecipeTypes.CANNER_RECIPES)
                .setProgressBar(new ProgressTexture(emptyTexture, filledTexture));
        AtomicReference<Double> suppliedProgress = new AtomicReference<>(0.25);

        UI ui = recipeUI.createLDLib2UITemplate(suppliedProgress::get, HashBasedTable.create(),
                DataComponentMap.EMPTY, List.of());
        List<GTProgressBarElement> progressBars = ui.selectId("progress", GTProgressBarElement.class).toList();
        helper.assertTrue(progressBars.size() == 1,
                "default programmatic recipe template did not contain exactly one progress bar");
        GTProgressBarElement progressBar = progressBars.getFirst();
        refreshStyle(progressBar);

        helper.assertTrue(progressBar.getEmptyBarTexture() == emptyTexture &&
                progressBar.getFilledBarTexture() == filledTexture,
                "default programmatic recipe progress did not retain its configured textures");
        progressBar.screenTick();
        GTProgressBarElement.ProgressDrawArea firstDrawArea = progressBar.getProgressDrawArea(0, 0, 100, 20);

        suppliedProgress.set(0.75);
        progressBar.screenTick();
        GTProgressBarElement.ProgressDrawArea secondDrawArea = progressBar.getProgressDrawArea(0, 0, 100, 20);

        helper.assertTrue(firstDrawArea.width() > 0 && secondDrawArea.width() > firstDrawArea.width(),
                "default programmatic recipe progress crop did not advance with its supplier");
        helper.succeed();
    }

    private static void refreshStyle(GTProgressBarElement progressBar) {
        progressBar.getStyleBag().compute(0);
    }

    private static void assertArea(GameTestHelper helper, GTProgressBarElement.ProgressDrawArea actual,
                                   float x, float y, float width, float height, float drawnU, float drawnV,
                                   float drawnWidth, float drawnHeight, String direction) {
        assertClose(helper, actual.x(), x, direction + " x");
        assertClose(helper, actual.y(), y, direction + " y");
        assertClose(helper, actual.width(), width, direction + " width");
        assertClose(helper, actual.height(), height, direction + " height");
        assertClose(helper, actual.drawnU(), drawnU, direction + " drawn U");
        assertClose(helper, actual.drawnV(), drawnV, direction + " drawn V");
        assertClose(helper, actual.drawnWidth(), drawnWidth, direction + " drawn width");
        assertClose(helper, actual.drawnHeight(), drawnHeight, direction + " drawn height");
    }

    private static void assertClose(GameTestHelper helper, float actual, float expected, String field) {
        helper.assertTrue(Math.abs(actual - expected) < EPSILON,
                field + " was " + actual + " instead of " + expected);
    }

    private static final class RecordingTexture implements IGuiTexture {

        @Override
        public void draw(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                         float height, float partialTicks) {}
    }
}
