package com.invokingmachines.multistorage.dto.query;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(Criteria.class),
        @JsonSubTypes.Type(Criterion.class)
})
public abstract class Node {
}
