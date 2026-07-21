package com.gregtechceu.gtceu.api.recipe.ui;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.ProgressTexture;
import com.gregtechceu.gtceu.api.gui.texture.ResourceBorderTexture;
import com.gregtechceu.gtceu.api.gui.texture.ResourceTexture;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import it.unimi.dsi.fastutil.objects.Object2IntAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2IntSortedMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Builds and parses the saveable LDLib2 XML representation of a recipe UI template.
 */
final class RecipeUIXmlTemplate {

    private static final String XSD_LOCATION = "https://raw.githubusercontent.com/Low-Drag-MC/LDLib2/refs/heads/1.21/ldlib2-ui.xsd";

    private RecipeUIXmlTemplate() {}

    static Document createDocument(GTRecipeType recipeType, GTRecipeTypeUI recipeUI) {
        Document document = newDocument();
        Element documentRoot = document.createElement("ldlib2-ui");
        documentRoot.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:xsi",
                XMLConstants.W3C_XML_SCHEMA_NS_URI);
        documentRoot.setAttributeNS(XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "xsi:noNamespaceSchemaLocation",
                XSD_LOCATION);
        document.appendChild(documentRoot);

        Element stylesheet = document.createElement("stylesheet");
        stylesheet.setAttribute("location", "ldlib2:lss/mc.lss");
        documentRoot.appendChild(stylesheet);

        XmlElementGroup inputs = createInventoryGroup(document, recipeType.maxInputs, false, recipeUI);
        XmlElementGroup outputs = createInventoryGroup(document, recipeType.maxOutputs, true, recipeUI);
        int maxWidth = Math.max(inputs.width(), outputs.width());
        int width = 2 * maxWidth + 40;
        int height = Math.max(inputs.height(), outputs.height());

        Element root = document.createElement("root");
        root.setAttribute("source-format", "gtm-generated-recipe-template");
        root.setAttribute("style", relativeStyle(width, height));
        documentRoot.appendChild(root);

        inputs.element().setAttribute("style", absoluteStyle(
                (maxWidth - inputs.width()) / 2, (height - inputs.height()) / 2,
                inputs.width(), inputs.height()));
        outputs.element().setAttribute("style", absoluteStyle(
                maxWidth + 40 + (maxWidth - outputs.width()) / 2, (height - outputs.height()) / 2,
                outputs.width(), outputs.height()));
        root.appendChild(inputs.element());
        root.appendChild(outputs.element());
        root.appendChild(createProgressElement(document, recipeUI, maxWidth + 10, height / 2 - 10));
        return document;
    }

    static Document parse(String xml) {
        try (StringReader reader = new StringReader(xml)) {
            var documentBuilder = secureDocumentBuilderFactory().newDocumentBuilder();
            return documentBuilder.parse(new InputSource(reader));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            GTCEu.LOGGER.error("Failed to parse LDLib2 recipe UI XML", e);
            throw new IllegalArgumentException("Invalid LDLib2 recipe UI XML", e);
        }
    }

    static String serialize(Document document) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            GTCEu.LOGGER.error("Failed to serialize generated LDLib2 recipe UI XML", e);
            throw new IllegalStateException("Failed to serialize generated LDLib2 recipe UI XML", e);
        }
    }

    private static Document newDocument() {
        try {
            return secureDocumentBuilderFactory().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            GTCEu.LOGGER.error("Failed to create LDLib2 recipe UI XML document", e);
            throw new IllegalStateException("Failed to create LDLib2 recipe UI XML document", e);
        }
    }

    private static DocumentBuilderFactory secureDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private static XmlElementGroup createInventoryGroup(Document document,
                                                        Object2IntSortedMap<RecipeCapability<?>> capabilities,
                                                        boolean output,
                                                        GTRecipeTypeUI recipeUI) {
        int maxCount = 0;
        int totalRows = 0;
        Object2IntSortedMap<RecipeCapability<?>> renderedCapabilities = new Object2IntAVLTreeMap<>(
                RecipeCapability.COMPARATOR);
        for (var entry : capabilities.object2IntEntrySet()) {
            RecipeCapability<?> capability = entry.getKey();
            if (!capability.doRenderSlot) {
                continue;
            }
            int count = entry.getIntValue();
            maxCount = Math.max(maxCount, Math.min(count, 3));
            totalRows += (count + 2) / 3;
            renderedCapabilities.put(capability, count);
        }

        int width = maxCount * 18 + 8;
        int height = totalRows * 18 + 8;
        Element group = document.createElement("element");
        int layoutIndex = 0;
        for (var entry : renderedCapabilities.object2IntEntrySet()) {
            RecipeCapability<?> capability = entry.getKey();
            int capabilityCount = entry.getIntValue();
            for (int slotIndex = 0; slotIndex < capabilityCount; slotIndex++) {
                Element slot = capability.createLDLib2XmlElement(document);
                if (slot == null) {
                    IO io = output ? IO.OUT : IO.IN;
                    GTCEu.LOGGER.error("Recipe capability '{}' declares a rendered {} slot without an XML element",
                            capability.name, io);
                    throw new IllegalStateException("Missing LDLib2 recipe XML element for capability " +
                            capability.name + " " + io);
                }
                slot.setAttribute("id", capability.slotName(output ? IO.OUT : IO.IN, slotIndex));
                slot.setAttribute("style", absoluteStyle(
                        (layoutIndex % 3) * 18 + 4, (layoutIndex / 3) * 18 + 4, 18, 18));
                IGuiTexture overlay = recipeUI.getSlotOverlays().get(slotOverlayKey(
                        output, capability == FluidRecipeCapability.CAP, slotIndex == capabilityCount - 1));
                if (overlay != null) {
                    slot.setAttribute("legacy-overlay", textureMetadata(overlay));
                }
                group.appendChild(slot);
                layoutIndex++;
            }
            layoutIndex += (3 - layoutIndex % 3) % 3;
        }
        return new XmlElementGroup(group, width, height);
    }

    private static Element createProgressElement(Document document, GTRecipeTypeUI recipeUI, int left, int top) {
        ProgressTexture progressTexture = recipeUI.getProgressBarTexture();
        Element progress = document.createElement("gtm-progress-bar");
        progress.setAttribute("id", "progress");
        progress.setAttribute("style", absoluteStyle(left, top, 20, 20));
        progress.setAttribute("fill-direction", switch (progressTexture.getFillDirection()) {
            case RIGHT_TO_LEFT -> "RIGHT_TO_LEFT";
            case UP_TO_DOWN -> "UP_TO_DOWN";
            case DOWN_TO_UP -> "DOWN_TO_UP";
            case LEFT_TO_RIGHT, ALWAYS_FULL -> "LEFT_TO_RIGHT";
        });
        progress.setAttribute("legacy-empty-bar", textureMetadata(progressTexture.getEmptyBarArea()));
        progress.setAttribute("legacy-filled-bar", textureMetadata(progressTexture.getFilledBarArea()));
        return progress;
    }

    private static byte slotOverlayKey(boolean output, boolean fluid, boolean last) {
        return (byte) ((output ? 2 : 0) + (fluid ? 1 : 0) + (last ? 4 : 0));
    }

    private static String textureMetadata(IGuiTexture texture) {
        if (texture == IGuiTexture.EMPTY) {
            return "empty";
        }
        if (texture instanceof ResourceBorderTexture borderTexture) {
            return "border_texture:" + borderTexture.imageLocation;
        }
        if (texture instanceof ResourceTexture resourceTexture) {
            String metadata = "resource_texture:" + resourceTexture.imageLocation;
            if (resourceTexture.offsetX == 0 && resourceTexture.offsetY == 0 &&
                    resourceTexture.imageWidth == 1 && resourceTexture.imageHeight == 1) {
                return metadata;
            }
            return metadata + "@" + resourceTexture.offsetX + "," + resourceTexture.offsetY + "," +
                    resourceTexture.imageWidth + "," + resourceTexture.imageHeight;
        }
        GTCEu.LOGGER.error("Unsupported recipe XML texture type {}", texture.getClass().getName());
        throw new IllegalArgumentException("Unsupported recipe XML texture type: " + texture.getClass().getName());
    }

    private static String relativeStyle(int width, int height) {
        return "position: relative; width: %d; height: %d;".formatted(width, height);
    }

    private static String absoluteStyle(int left, int top, int width, int height) {
        return "position: absolute; left: %d; top: %d; width: %d; height: %d;"
                .formatted(left, top, width, height);
    }

    private record XmlElementGroup(Element element, int width, int height) {}
}
