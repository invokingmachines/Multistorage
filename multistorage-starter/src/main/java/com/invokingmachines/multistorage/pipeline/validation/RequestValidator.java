package com.invokingmachines.multistorage.pipeline.validation;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.pipeline.OperationType;

public interface RequestValidator<T> {

    OperationType getOperationType();

    QueryMeta validate(T request, QueryMeta fullMeta, String targetTableName);
}
