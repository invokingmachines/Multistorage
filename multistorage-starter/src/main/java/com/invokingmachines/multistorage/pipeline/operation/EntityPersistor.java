package com.invokingmachines.multistorage.pipeline.operation;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;

import java.util.Map;

public interface EntityPersistor {

    Map<String, Object> upsert(String targetTableName, Map<String, Object> entity, QueryMeta meta);

    void delete(String targetTableName, Object id, QueryMeta meta);
}
