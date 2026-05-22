package com.gregtechceu.gtceu.api.multiblock;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

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
