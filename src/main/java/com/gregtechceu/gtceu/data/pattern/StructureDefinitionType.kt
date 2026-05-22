package com.gregtechceu.gtceu.data.pattern;

import org.jetbrains.annotations.Nullable;

public enum StructureDefinitionType {

    SERIALIZED_BLOCK_PATTERN("binary", ".cbor.zst"),
    STRING_ARRAY_JSON("json", ".json");

    public final String directoryName;
    public final String fileExtension;

    StructureDefinitionType(String directoryName, String fileExtension) {
        this.directoryName = directoryName;
        this.fileExtension = fileExtension;
    }

    public boolean matchesFileName(String fileName) {
        return fileName.endsWith(fileExtension);
    }

    public String stripFileExtension(String fileName) {
        if (!matchesFileName(fileName)) {
            throw new IllegalArgumentException(
                    "File '" + fileName + "' does not match extension '" + fileExtension + "'");
        }
        return fileName.substring(0, fileName.length() - fileExtension.length());
    }

    public static @Nullable StructureDefinitionType fromDirectoryName(String directoryName) {
        for (StructureDefinitionType value : values()) {
            if (value.directoryName.equals(directoryName)) {
                return value;
            }
        }
        return null;
    }
}
