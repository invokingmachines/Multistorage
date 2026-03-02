package com.invokingmachines.multistorage.pipeline;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;

public interface PostProcessHandler<T, R> {

    OperationType getOperationType();

    void postProcess(T request, QueryMeta meta, R response);
}
