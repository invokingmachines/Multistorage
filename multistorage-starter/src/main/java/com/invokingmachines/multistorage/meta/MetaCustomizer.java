package com.invokingmachines.multistorage.meta;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.meta.dto.MetaRequest;

@FunctionalInterface
public interface MetaCustomizer {

    QueryMeta customize(QueryMeta meta, MetaRequest request);
}
