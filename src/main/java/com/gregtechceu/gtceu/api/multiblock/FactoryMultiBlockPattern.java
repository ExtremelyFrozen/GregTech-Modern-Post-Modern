package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * File-backed multiblock definition DTO.
 * <p>
 * This class intentionally focuses on storage shape only, so structure definitions can be
 * serialized to or deserialized from {@code pattern/<modid>/json/*.json}.
 * Runtime conversion to {@link BlockPattern} is expected to be handled separately during the
 * structure-definition refactor.
 */
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FactoryMultiBlockPattern {

    private int formatVersion = 1;
    private AxisMapping axisMapping = AxisMapping.defaultMapping();
    private List<LayerGroup> layerGroups = new ArrayList<>();
    private Map<String, PredicateReference> predicates = new LinkedHashMap<>();
    private PlacementMetadata placement = new PlacementMetadata();
    private Map<String, Object> extensions = new LinkedHashMap<>();

    public FactoryMultiBlockPattern() {}

    @JsonCreator
    public FactoryMultiBlockPattern(@JsonProperty("formatVersion") Integer formatVersion,
                                    @JsonProperty("axisMapping") AxisMapping axisMapping,
                                    @JsonProperty("layerGroups") List<LayerGroup> layerGroups,
                                    @JsonProperty("predicates") Map<String, PredicateReference> predicates,
                                    @JsonProperty("placement") PlacementMetadata placement,
                                    @JsonProperty("extensions") Map<String, Object> extensions) {
        setFormatVersion(formatVersion == null ? 1 : formatVersion);
        setAxisMapping(axisMapping == null ? AxisMapping.defaultMapping() : axisMapping);
        setLayerGroups(layerGroups == null ? List.of() : layerGroups);
        setPredicates(predicates == null ? Map.of() : predicates);
        setPlacement(placement == null ? new PlacementMetadata() : placement);
        setExtensions(extensions == null ? Map.of() : extensions);
    }

    public void setFormatVersion(int formatVersion) {
        if (formatVersion < 1) {
            throw new IllegalArgumentException("formatVersion must be >= 1");
        }
        this.formatVersion = formatVersion;
    }

    public void setAxisMapping(AxisMapping axisMapping) {
        this.axisMapping = Objects.requireNonNull(axisMapping, "axisMapping");
        this.axisMapping.validate();
    }

    public void setLayerGroups(List<LayerGroup> layerGroups) {
        Objects.requireNonNull(layerGroups, "layerGroups");
        List<LayerGroup> copy = new ArrayList<>(layerGroups.size());
        for (LayerGroup group : layerGroups) {
            LayerGroup nonNullGroup = Objects.requireNonNull(group, "layerGroups contains null entry");
            nonNullGroup.validate();
            copy.add(nonNullGroup);
        }
        this.layerGroups = copy;
    }

    public void setPredicates(Map<String, PredicateReference> predicates) {
        Objects.requireNonNull(predicates, "predicates");
        Map<String, PredicateReference> copy = new LinkedHashMap<>(predicates.size());
        for (Map.Entry<String, PredicateReference> entry : predicates.entrySet()) {
            String symbol = normalizeSymbol(entry.getKey());
            PredicateReference value = Objects.requireNonNull(entry.getValue(),
                    "predicates contains null value for symbol " + symbol);
            copy.put(symbol, value);
        }
        this.predicates = copy;
    }

    public void setPlacement(PlacementMetadata placement) {
        this.placement = Objects.requireNonNull(placement, "placement");
    }

    public void setExtensions(Map<String, Object> extensions) {
        Objects.requireNonNull(extensions, "extensions");
        this.extensions = new LinkedHashMap<>(extensions);
    }

    /**
     * Validates the storage model without assuming runtime predicate resolution is available.
     */
    public void validate() {
        axisMapping.validate();
        if (layerGroups.isEmpty()) {
            throw new IllegalStateException("layerGroups must not be empty");
        }
        int expectedHeight = -1;
        int expectedWidth = -1;
        for (LayerGroup group : layerGroups) {
            group.validate();
            for (Layer layer : group.layers) {
                if (expectedHeight == -1) {
                    expectedHeight = layer.rows.size();
                    expectedWidth = layer.rows.getFirst().length();
                } else if (layer.rows.size() != expectedHeight) {
                    throw new IllegalStateException("All layers must share the same row count");
                } else if (layer.rows.getFirst().length() != expectedWidth) {
                    throw new IllegalStateException("All layers must share the same row width");
                }
            }
        }
    }

    @JsonIgnore
    public int getTotalDistinctLayerCount() {
        return layerGroups.stream().mapToInt(group -> group.layers.size()).sum();
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.length() != 1) {
            throw new IllegalArgumentException("Predicate keys must be exactly one character");
        }
        return symbol;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AxisMapping {

        private RelativeDirection charDirection = RelativeDirection.LEFT;
        private RelativeDirection rowDirection = RelativeDirection.UP;
        private RelativeDirection aisleDirection = RelativeDirection.FRONT;

        public AxisMapping() {}

        @JsonCreator
        public AxisMapping(@JsonProperty("charDirection") RelativeDirection charDirection,
                           @JsonProperty("rowDirection") RelativeDirection rowDirection,
                           @JsonProperty("aisleDirection") RelativeDirection aisleDirection) {
            this.charDirection = charDirection == null ? RelativeDirection.LEFT : charDirection;
            this.rowDirection = rowDirection == null ? RelativeDirection.UP : rowDirection;
            this.aisleDirection = aisleDirection == null ? RelativeDirection.FRONT : aisleDirection;
        }

        public static AxisMapping defaultMapping() {
            return new AxisMapping(RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.FRONT);
        }

        public void setCharDirection(RelativeDirection charDirection) {
            this.charDirection = Objects.requireNonNull(charDirection, "charDirection");
        }

        public void setRowDirection(RelativeDirection rowDirection) {
            this.rowDirection = Objects.requireNonNull(rowDirection, "rowDirection");
        }

        public void setAisleDirection(RelativeDirection aisleDirection) {
            this.aisleDirection = Objects.requireNonNull(aisleDirection, "aisleDirection");
        }

        public void validate() {
            int flags = 0;
            for (RelativeDirection direction : new RelativeDirection[] { charDirection, rowDirection,
                    aisleDirection }) {
                switch (direction) {
                    case UP, DOWN -> flags |= 0x1;
                    case LEFT, RIGHT -> flags |= 0x2;
                    case FRONT, BACK -> flags |= 0x4;
                }
            }
            if (flags != 0x7) {
                throw new IllegalStateException("Axis mapping must span 3 different axes");
            }
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LayerGroup {

        @Setter
        private String id;
        @Setter
        private int minRepeat = 1;
        @Setter
        private int maxRepeat = 1;
        private List<Layer> layers = new ArrayList<>();
        @Setter
        private String note;

        public LayerGroup() {}

        @JsonCreator
        public LayerGroup(@JsonProperty("id") String id,
                          @JsonProperty("minRepeat") Integer minRepeat,
                          @JsonProperty("maxRepeat") Integer maxRepeat,
                          @JsonProperty("layers") List<Layer> layers,
                          @JsonProperty("note") String note) {
            this.id = id;
            this.minRepeat = minRepeat == null ? 1 : minRepeat;
            this.maxRepeat = maxRepeat == null ? 1 : maxRepeat;
            this.note = note;
            setLayers(layers == null ? List.of() : layers);
        }

        public void setLayers(List<Layer> layers) {
            Objects.requireNonNull(layers, "layers");
            List<Layer> copy = new ArrayList<>(layers.size());
            for (Layer layer : layers) {
                Layer nonNullLayer = Objects.requireNonNull(layer, "layers contains null entry");
                nonNullLayer.validate();
                copy.add(nonNullLayer);
            }
            this.layers = copy;
        }

        public void validate() {
            if (minRepeat < 0) {
                throw new IllegalStateException("minRepeat must be >= 0");
            }
            if (maxRepeat < minRepeat) {
                throw new IllegalStateException("maxRepeat must be >= minRepeat");
            }
            if (layers.isEmpty()) {
                throw new IllegalStateException("Layer group must contain at least one layer");
            }
            int expectedHeight = -1;
            int expectedWidth = -1;
            for (Layer layer : layers) {
                layer.validate();
                if (expectedHeight == -1) {
                    expectedHeight = layer.rows.size();
                    expectedWidth = layer.rows.getFirst().length();
                } else if (layer.rows.size() != expectedHeight) {
                    throw new IllegalStateException("All layers in the same group must share the same row count");
                } else if (layer.rows.getFirst().length() != expectedWidth) {
                    throw new IllegalStateException("All layers in the same group must share the same row width");
                }
            }
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Layer {

        @Setter
        private String id;
        private List<String> rows = new ArrayList<>();
        @Setter
        private String note;

        public Layer() {}

        @JsonCreator
        public Layer(@JsonProperty("id") String id,
                     @JsonProperty("rows") List<String> rows,
                     @JsonProperty("note") String note) {
            this.id = id;
            this.note = note;
            setRows(rows == null ? List.of() : rows);
        }

        public void setRows(List<String> rows) {
            Objects.requireNonNull(rows, "rows");
            List<String> copy = new ArrayList<>(rows.size());
            int width = -1;
            for (String row : rows) {
                if (row == null || row.isEmpty()) {
                    throw new IllegalArgumentException("Layer rows must not be null or empty");
                }
                if (width == -1) {
                    width = row.length();
                } else if (row.length() != width) {
                    throw new IllegalArgumentException("All rows in a layer must share the same width");
                }
                copy.add(row);
            }
            this.rows = copy;
        }

        public void validate() {
            if (rows.isEmpty()) {
                throw new IllegalStateException("Layer must contain at least one row");
            }
            int width = rows.getFirst().length();
            if (width == 0) {
                throw new IllegalStateException("Layer rows must not be empty");
            }
            for (String row : rows) {
                if (row.length() != width) {
                    throw new IllegalStateException("All rows in a layer must share the same width");
                }
            }
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PredicateReference {

        @Setter
        private String kind;
        private Map<String, Object> config = new LinkedHashMap<>();
        @Setter
        private boolean controller;
        @Setter
        private int minGlobalCount;
        @Setter
        private int maxGlobalCount;
        @Setter
        private int minLayerCount;
        @Setter
        private int maxLayerCount;
        @Setter
        private int previewCount;
        @Setter
        private String io;
        @Setter
        private String slotName;
        @Setter
        private String nbtParser;
        @Setter
        private boolean disableRenderFormed;
        private List<String> tooltips = new ArrayList<>();

        public PredicateReference() {}

        @JsonCreator
        public PredicateReference(@JsonProperty("kind") String kind,
                                  @JsonProperty("config") Map<String, Object> config,
                                  @JsonProperty("controller") boolean controller,
                                  @JsonProperty("minGlobalCount") int minGlobalCount,
                                  @JsonProperty("maxGlobalCount") int maxGlobalCount,
                                  @JsonProperty("minLayerCount") int minLayerCount,
                                  @JsonProperty("maxLayerCount") int maxLayerCount,
                                  @JsonProperty("previewCount") int previewCount,
                                  @JsonProperty("io") String io,
                                  @JsonProperty("slotName") String slotName,
                                  @JsonProperty("nbtParser") String nbtParser,
                                  @JsonProperty("disableRenderFormed") boolean disableRenderFormed,
                                  @JsonProperty("tooltips") List<String> tooltips) {
            this.kind = kind;
            setConfig(config == null ? Map.of() : config);
            this.controller = controller;
            this.minGlobalCount = minGlobalCount;
            this.maxGlobalCount = maxGlobalCount;
            this.minLayerCount = minLayerCount;
            this.maxLayerCount = maxLayerCount;
            this.previewCount = previewCount;
            this.io = io;
            this.slotName = slotName;
            this.nbtParser = nbtParser;
            this.disableRenderFormed = disableRenderFormed;
            setTooltips(tooltips == null ? List.of() : tooltips);
        }

        public void setConfig(Map<String, Object> config) {
            Objects.requireNonNull(config, "config");
            this.config = new LinkedHashMap<>(config);
        }

        public void setTooltips(List<String> tooltips) {
            Objects.requireNonNull(tooltips, "tooltips");
            this.tooltips = new ArrayList<>(tooltips);
        }
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlacementMetadata {

        @Setter
        private int schemaVersion = 1;
        @Setter
        private boolean storeFacing = Boolean.TRUE;
        @Setter
        private boolean storeUpwardsFacing = Boolean.TRUE;
        @Setter
        private boolean storeFlip = Boolean.TRUE;
        @Setter
        private String orientationNote;
        private Map<String, Object> autoBuild = new LinkedHashMap<>();

        public PlacementMetadata() {}

        @JsonCreator
        public PlacementMetadata(@JsonProperty("schemaVersion") Integer schemaVersion,
                                 @JsonProperty("storeFacing") Boolean storeFacing,
                                 @JsonProperty("storeUpwardsFacing") Boolean storeUpwardsFacing,
                                 @JsonProperty("storeFlip") Boolean storeFlip,
                                 @JsonProperty("orientationNote") String orientationNote,
                                 @JsonProperty("autoBuild") Map<String, Object> autoBuild) {
            this.schemaVersion = schemaVersion == null ? 1 : schemaVersion;
            this.storeFacing = storeFacing == null ? Boolean.TRUE : storeFacing;
            this.storeUpwardsFacing = storeUpwardsFacing == null ? Boolean.TRUE : storeUpwardsFacing;
            this.storeFlip = storeFlip == null ? Boolean.TRUE : storeFlip;
            this.orientationNote = orientationNote;
            setAutoBuild(autoBuild == null ? Map.of() : autoBuild);
        }

        public void setAutoBuild(Map<String, Object> autoBuild) {
            Objects.requireNonNull(autoBuild, "autoBuild");
            this.autoBuild = new LinkedHashMap<>(autoBuild);
        }
    }
}
