package com.invokingmachines.multistorage.pipeline;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;

public interface PreProcessHandler<T> {

    OperationType getOperationType();

    void preProcess(T request, QueryMeta meta, String targetTableName);
}
