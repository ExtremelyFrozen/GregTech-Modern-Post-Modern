package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import dev.vfyjxf.taffy.style.TaffyDimension;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Describes an editable LDLib2 machine XML template and binds a parsed template to a concrete machine.
 */
public class EditableMachineUI {

    @Getter
    private final String groupName;
    @Getter
    private final ResourceLocation uiPath;
    private final Supplier<String> defaultXmlSupplier;
    private final BiConsumer<UIElement, MetaMachine> binder;
    @Nullable
    private String customXmlCache;
    @Nullable
    private ResourceManager customXmlCacheOwner;
    private boolean customXmlCacheLoaded;

    public EditableMachineUI(String groupName, ResourceLocation uiPath, Supplier<String> defaultXmlSupplier,
                             BiConsumer<UIElement, MetaMachine> binder) {
        this.groupName = groupName;
        this.uiPath = uiPath;
        this.defaultXmlSupplier = defaultXmlSupplier;
        this.binder = binder;
    }

    /**
     * Creates fresh default XML for template selection in the GT XML editor.
     *
     * @return serialized, unbound LDLib2 machine XML.
     */
    public String getDefaultXml() {
        String xml = defaultXmlSupplier.get();
        if (xml == null || xml.isBlank()) {
            GTCEu.LOGGER.error("Default machine UI XML supplier returned no XML for {}", uiPath);
            throw new IllegalStateException("Default machine UI XML is empty for " + uiPath);
        }
        return xml;
    }

    /**
     * Resolves the resource-pack location used by this machine template.
     *
     * @return {@code assets/<namespace>/ui/machine/<path>.xml} as a resource location.
     */
    public ResourceLocation getXmlLocation() {
        return ResourceLocation.fromNamespaceAndPath(uiPath.getNamespace(),
                "ui/machine/%s.xml".formatted(uiPath.getPath()));
    }

    /**
     * Parses the default template without applying runtime bindings.
     *
     * @return a fresh LDLib2 UI suitable for editor preview.
     */
    public UI createDefaultUI() {
        return parseUI(getDefaultXml(), "default template for " + uiPath);
    }

    /**
     * Loads the resource-pack override when present, otherwise parses the default template, then binds the machine.
     *
     * @param resourceManager resources visible to the current logical side.
     * @param machine         concrete machine backing the parsed elements.
     * @return a fresh, bound LDLib2 UI.
     */
    public UI createUI(ResourceManager resourceManager, MetaMachine machine) {
        UI ui = parseUI(getRuntimeXml(resourceManager), getXmlLocation().toString());
        setupUI(ui.rootElement, machine);
        return ui;
    }

    /**
     * Applies this template's runtime bindings to a parsed machine UI tree.
     *
     * @param root    parsed machine UI root.
     * @param machine concrete machine backing the elements.
     */
    public void setupUI(UIElement root, MetaMachine machine) {
        try {
            binder.accept(root, machine);
        } catch (RuntimeException e) {
            GTCEu.LOGGER.error("Failed to bind machine UI {} to {}", getXmlLocation(), machine.getDefinition().getId(),
                    e);
            throw e;
        }
    }

    /**
     * Reads the fixed pixel dimensions declared by the active machine XML root.
     *
     * @param resourceManager resources visible to the current logical side.
     * @return active machine page dimensions.
     */
    public MachineUISize getSize(ResourceManager resourceManager) {
        UIElement root = parseUI(getRuntimeXml(resourceManager), getXmlLocation().toString()).rootElement;
        return new MachineUISize(fixedDimension(root, true), fixedDimension(root, false));
    }

    /**
     * Invalidates the resource override cache after a resource-pack or editor save reload.
     */
    public synchronized void reloadCustomUI() {
        customXmlCache = null;
        customXmlCacheOwner = null;
        customXmlCacheLoaded = false;
    }

    private synchronized String getRuntimeXml(ResourceManager resourceManager) {
        if (!customXmlCacheLoaded || customXmlCacheOwner != resourceManager) {
            ResourceLocation location = getXmlLocation();
            var resource = resourceManager.getResource(location);
            if (resource.isPresent()) {
                try (InputStream inputStream = resource.get().open()) {
                    customXmlCache = decodeUtf8(inputStream.readAllBytes(), location);
                } catch (Exception e) {
                    GTCEu.LOGGER.error("Failed to load machine UI XML from {}", location, e);
                    throw new IllegalStateException("Failed to load machine UI XML from " + location, e);
                }
            } else {
                customXmlCache = null;
            }
            customXmlCacheOwner = resourceManager;
            customXmlCacheLoaded = true;
        }
        return customXmlCache == null ? getDefaultXml() : customXmlCache;
    }

    private static UI parseUI(String xml, String source) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            try (StringReader reader = new StringReader(xml)) {
                return UI.of(builder.parse(new InputSource(reader)));
            }
        } catch (Exception e) {
            GTCEu.LOGGER.error("Failed to parse LDLib2 machine UI XML from {}", source, e);
            throw new IllegalStateException("Failed to parse LDLib2 machine UI XML from " + source, e);
        }
    }

    private static String decodeUtf8(byte[] bytes, ResourceLocation location) throws CharacterCodingException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String xml = decoder.decode(ByteBuffer.wrap(bytes)).toString();
        if (!xml.isEmpty() && xml.charAt(0) == '\uFEFF') {
            throw new IllegalArgumentException("Machine UI XML must be UTF-8 without BOM: " + location);
        }
        return xml;
    }

    private static int fixedDimension(UIElement root, boolean width) {
        TaffyDimension dimension = width ? root.getLayout().getWidth() : root.getLayout().getHeight();
        if (dimension == null || !dimension.isLength()) {
            dimension = root.getStyleBag().computeCandidate(width ? LayoutProperties.WIDTH : LayoutProperties.HEIGHT);
        }
        if (dimension == null || !dimension.isLength()) {
            String axis = width ? "width" : "height";
            GTCEu.LOGGER.error("LDLib2 machine UI {} must use a fixed pixel size, got {}", axis, dimension);
            throw new IllegalArgumentException("LDLib2 machine UI " + axis + " must use a fixed pixel size");
        }
        return Math.round(dimension.getValue());
    }

    /**
     * Fixed dimensions declared by a machine XML root.
     *
     * @param width  root width in pixels.
     * @param height root height in pixels.
     */
    public record MachineUISize(int width, int height) {}
}
