package com.invokingmachines.multistorage.dto.meta;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.Map;


@Data
@Builder
public class QueryMeta {

    @Singular
    private Map<String, TableMeta> tables;
}
