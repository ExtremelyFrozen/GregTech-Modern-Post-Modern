package com.gregtechceu.gtceu.data.pattern;

public enum StructureDefinitionSource {

    JAVA("java"),
    JSON("json"),
    BINARY_JSON("binary");

    private final String serializedName;

    StructureDefinitionSource(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public static StructureDefinitionSource fromDefinitionType(StructureDefinitionType type) {
        return switch (type) {
            case STRING_ARRAY_JSON -> JSON;
            case SERIALIZED_BLOCK_PATTERN -> BINARY_JSON;
        };
    }
}
