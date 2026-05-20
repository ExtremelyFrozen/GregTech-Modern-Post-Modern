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

    public FactoryMultiBlockPattern() {}
}
