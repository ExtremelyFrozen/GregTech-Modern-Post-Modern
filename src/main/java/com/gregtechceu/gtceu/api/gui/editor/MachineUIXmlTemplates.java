package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Builds the default LDLib2 XML documents used by editable tiered machine pages.
 */
public final class MachineUIXmlTemplates {

    public static final String RECIPE_TEMPLATE_ID = "recipe_template";
    public static final String BATTERY_SLOT_ID = "battery_slot";
    public static final String ENERGY_BAR_ID = "energy_container";

    private static final String XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String SCHEMA_LOCATION = "https://raw.githubusercontent.com/Low-Drag-MC/LDLib2/refs/heads/1.21/ldlib2-ui.xsd";
    private static final int SIMPLE_MINIMUM_HEIGHT = 78;
    private static final int GENERATOR_ENERGY_BAR_WIDTH = 18;
    private static final int GENERATOR_ENERGY_BAR_HEIGHT = 60;
    private static final int GENERATOR_RECIPE_ENERGY_GAP = 4;
    private static final int GENERATOR_HORIZONTAL_PADDING = 8;
    private static final int GENERATOR_VERTICAL_PADDING = 8;
    private static final int GENERATOR_MINIMUM_WIDTH = 172;
    private static final int GENERATOR_ENERGY_BAR_X = 3;

    private MachineUIXmlTemplates() {}

    /**
     * Wraps an active recipe XML document in the default simple-machine layout with its battery slot.
     *
     * @param recipeDocument active, unbound recipe template document.
     * @return serialized machine XML suitable for editing or runtime loading.
     */
    public static String createSimpleMachineXml(Document recipeDocument) {
        try {
            FixedSize recipeSize = getFixedSize(recipeDocument);
            int width = recipeSize.width();
            int height = Math.max(recipeSize.height(), SIMPLE_MINIMUM_HEIGHT);
            Document machineDocument = createMachineDocument(recipeDocument, width, height);
            Element root = findSingleDirectChild(machineDocument.getDocumentElement(), "root");

            appendRecipeTemplate(machineDocument, root, recipeDocument, 0,
                    (height - recipeSize.height()) / 2, recipeSize);

            Element batterySlot = machineDocument.createElement("gtm-item-slot");
            batterySlot.setAttribute("id", BATTERY_SLOT_ID);
            batterySlot.setAttribute("style", absoluteBounds(width / 2 - 9, height - 18, 18, 18));
            batterySlot.setAttribute("draw-hover-overlay", "true");
            batterySlot.setAttribute("draw-hover-tips", "true");
            batterySlot.setAttribute("can-put-items", "true");
            batterySlot.setAttribute("can-take-items", "true");
            batterySlot.setAttribute("legacy-background",
                    "border_texture:gtpm:textures/gui/base/slot.png");
            batterySlot.setAttribute("legacy-overlay",
                    "resource_texture:gtpm:textures/gui/overlay/charger_slot_overlay.png");
            root.appendChild(batterySlot);
            return serialize(machineDocument);
        } catch (RuntimeException e) {
            GTCEu.LOGGER.error("Failed to build the default simple machine XML template", e);
            throw e;
        }
    }

    /**
     * Wraps an active recipe XML document in the default generator layout with its energy bar.
     *
     * @param recipeDocument active, unbound recipe template document.
     * @return serialized machine XML suitable for editing or runtime loading.
     */
    public static String createGeneratorMachineXml(Document recipeDocument) {
        try {
            FixedSize recipeSize = getFixedSize(recipeDocument);
            int width = Math.max(GENERATOR_ENERGY_BAR_WIDTH + GENERATOR_RECIPE_ENERGY_GAP + recipeSize.width() +
                    GENERATOR_HORIZONTAL_PADDING, GENERATOR_MINIMUM_WIDTH);
            int height = Math.max(recipeSize.height() + GENERATOR_VERTICAL_PADDING,
                    GENERATOR_ENERGY_BAR_HEIGHT + GENERATOR_VERTICAL_PADDING);
            int recipeX = (width - GENERATOR_ENERGY_BAR_WIDTH - GENERATOR_RECIPE_ENERGY_GAP - recipeSize.width()) /
                    2 + GENERATOR_ENERGY_BAR_WIDTH + GENERATOR_RECIPE_ENERGY_GAP;
            Document machineDocument = createMachineDocument(recipeDocument, width, height);
            Element root = findSingleDirectChild(machineDocument.getDocumentElement(), "root");

            appendRecipeTemplate(machineDocument, root, recipeDocument, recipeX,
                    (height - recipeSize.height()) / 2, recipeSize);

            Element energyBar = machineDocument.createElement("gtm-progress-bar");
            energyBar.setAttribute("id", ENERGY_BAR_ID);
            energyBar.setAttribute("style", absoluteBounds(GENERATOR_ENERGY_BAR_X,
                    (height - GENERATOR_ENERGY_BAR_HEIGHT) / 2,
                    GENERATOR_ENERGY_BAR_WIDTH, GENERATOR_ENERGY_BAR_HEIGHT) +
                    " background: sprite(gtpm:textures/gui/progress_bar/progress_bar_boiler_empty_steel.png, " +
                    "0, 0, 10, 54, 1, 1, 1, 1);");
            energyBar.setAttribute("fill-direction", "DOWN_TO_UP");
            energyBar.setAttribute("legacy-empty-bar", "empty");
            energyBar.setAttribute("legacy-filled-bar",
                    "resource_texture:gtpm:textures/gui/progress_bar/progress_bar_boiler_heat.png");
            Element localStyle = machineDocument.createElement("style");
            // spotless:off
            localStyle.setTextContent("""
                    .__progress-bar_bar__ {
                        background: sprite(gtpm:textures/gui/progress_bar/progress_bar_boiler_heat.png,
                            0, 0, 10, 54, 1, 1, 1, 1);
                    }
                    """);
            // spotless:on
            energyBar.appendChild(localStyle);
            root.appendChild(energyBar);
            return serialize(machineDocument);
        } catch (RuntimeException e) {
            GTCEu.LOGGER.error("Failed to build the default generator machine XML template", e);
            throw e;
        }
    }

    /**
     * Resolves one required element from a parsed machine template.
     *
     * @param root root of the parsed machine UI.
     * @param id   required element identifier.
     * @param type required runtime element type.
     * @param <T>  runtime element type.
     * @return the single matching element.
     */
    public static <T> T requireElement(UIElement root, String id, Class<T> type) {
        var matches = root.selectId(id).toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Machine UI requires exactly one '" + id + "' element, found " +
                    matches.size());
        }
        UIElement match = matches.getFirst();
        if (!type.isInstance(match)) {
            throw new IllegalArgumentException("Machine UI element '" + id + "' must be " + type.getSimpleName() +
                    ", got " + match.getClass().getSimpleName());
        }
        return type.cast(match);
    }

    private static Document createMachineDocument(Document recipeDocument, int width, int height) {
        Document document = newDocument();
        Element documentRoot = document.createElement("ldlib2-ui");
        Element recipeDocumentRoot = recipeDocument.getDocumentElement();
        copyAttributes(recipeDocumentRoot, documentRoot);
        documentRoot.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xsi", XSI_NAMESPACE);
        documentRoot.setAttributeNS(XSI_NAMESPACE, "xsi:noNamespaceSchemaLocation", SCHEMA_LOCATION);
        document.appendChild(documentRoot);

        for (Node node = recipeDocumentRoot.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && !element.getTagName().equals("root")) {
                documentRoot.appendChild(document.importNode(element, true));
            }
        }

        Element root = document.createElement("root");
        root.setAttribute("style", relativeBounds(width, height));
        documentRoot.appendChild(root);
        return document;
    }

    private static void appendRecipeTemplate(Document machineDocument, Element machineRoot,
                                             Document recipeDocument, int x, int y, FixedSize recipeSize) {
        Element recipeRoot = findSingleDirectChild(recipeDocument.getDocumentElement(), "root");
        Element recipeGroup = machineDocument.createElement("element");
        copyAttributes(recipeRoot, recipeGroup);
        if (!recipeGroup.hasAttribute("id") || recipeGroup.getAttribute("id").isBlank()) {
            recipeGroup.setAttribute("id", RECIPE_TEMPLATE_ID);
        }
        recipeGroup.setAttribute("style", appendStyle(recipeRoot.getAttribute("style"),
                absoluteBounds(x, y, recipeSize.width(), recipeSize.height())));
        for (Node child = recipeRoot.getFirstChild(); child != null; child = child.getNextSibling()) {
            recipeGroup.appendChild(machineDocument.importNode(child, true));
        }
        machineRoot.appendChild(recipeGroup);
    }

    private static void copyAttributes(Element source, Element target) {
        NamedNodeMap attributes = source.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Attr attribute = (Attr) attributes.item(index);
            if (!attribute.getName().equals("style")) {
                target.setAttributeNS(attribute.getNamespaceURI(), attribute.getName(), attribute.getValue());
            }
        }
    }

    private static FixedSize getFixedSize(Document document) {
        UIElement root = UI.of(document).rootElement;
        var size = GTRecipeTypeUI.getLDLib2RecipeUISize(root);
        return new FixedSize(size.width(), size.height());
    }

    private static Element findSingleDirectChild(Element parent, String name) {
        Element result = null;
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals(name)) {
                if (result != null) {
                    throw new IllegalArgumentException("XML document contains more than one direct '" + name +
                            "' element");
                }
                result = element;
            }
        }
        if (result == null) {
            throw new IllegalArgumentException("XML document does not contain a direct '" + name + "' element");
        }
        return result;
    }

    private static Document newDocument() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder().newDocument();
        } catch (Exception e) {
            GTCEu.LOGGER.error("Failed to create an LDLib2 machine XML document", e);
            throw new IllegalStateException("Failed to create an LDLib2 machine XML document", e);
        }
    }

    private static String serialize(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            var writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            GTCEu.LOGGER.error("Failed to serialize an LDLib2 machine XML document", e);
            throw new IllegalStateException("Failed to serialize an LDLib2 machine XML document", e);
        }
    }

    private static String relativeBounds(int width, int height) {
        return "position: relative; width: %d; height: %d;".formatted(width, height);
    }

    private static String absoluteBounds(int x, int y, int width, int height) {
        return "position: absolute; left: %d; top: %d; width: %d; height: %d;"
                .formatted(x, y, width, height);
    }

    private static String appendStyle(String existingStyle, String appendedStyle) {
        String normalized = existingStyle.strip();
        if (!normalized.isEmpty() && !normalized.endsWith(";")) {
            normalized += ";";
        }
        return normalized.isEmpty() ? appendedStyle : normalized + " " + appendedStyle;
    }

    private record FixedSize(int width, int height) {}
}
