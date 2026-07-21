package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.gui.editor.UIXmlProject;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

public class GTUIXmlProject extends UIXmlProject {

    @Nullable
    private GTUIXmlTarget target;

    @Override
    public ProjectType getProjectType() {
        return GTUIXmlProjectType.TYPE;
    }

    @Override
    public void initNewProject() {
        super.initNewProject();
        target = null;
    }

    @Override
    public GTUIXmlProject setXml(String xml) {
        super.setXml(xml);
        return this;
    }

    @Override
    public GTUIXmlProject setXml(String xml, boolean notifyChanged) {
        super.setXml(xml, notifyChanged);
        return this;
    }

    public GTUIXmlProject useTemplate(GTUIXmlTarget target, String xml) {
        this.target = target;
        return setXml(xml);
    }

    public Optional<GTUIXmlTarget> getTarget() {
        return Optional.ofNullable(target);
    }

    void updateTarget(Path file) {
        target = GTUIXmlTarget.infer(file).orElse(null);
    }

    public UI createPreview() {
        try {
            return UI.of(parseDocument(getXml()));
        } catch (Exception e) {
            GTCEu.LOGGER.error("Failed to parse GT UI XML editor project", e);
            throw new IllegalArgumentException("Failed to parse GT UI XML editor project", e);
        }
    }

    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        try (StringReader reader = new StringReader(xml)) {
            return factory.newDocumentBuilder().parse(new InputSource(reader));
        }
    }
}
