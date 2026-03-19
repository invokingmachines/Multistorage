package com.invokingmachines.multistorage.pipeline.validation;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.pipeline.OperationType;
import com.invokingmachines.multistorage.pipeline.meta.QueryMetaFilter;
import com.invokingmachines.multistorage.query.service.QueryCompiler;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@org.springframework.stereotype.Component
public class DeleteRequestValidator implements RequestValidator<Object> {

    @Override
    public OperationType getOperationType() {
        return OperationType.DELETE;
    }

    @Override
    public QueryMeta validate(Object request, QueryMeta fullMeta, String targetTableName) {
        String tableName = QueryCompiler.resolveTargetToTableName(fullMeta, targetTableName);
        TableMeta table = fullMeta.getTables().get(tableName);
        if (table == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Table not found: " + targetTableName);
        }
        return QueryMetaFilter.subMeta(fullMeta, Set.of(tableName));
    }
}
