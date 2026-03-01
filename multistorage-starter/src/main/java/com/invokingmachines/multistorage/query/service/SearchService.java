package com.invokingmachines.multistorage.query.service;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.query.dto.CompiledQuery;
import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.meta.MetaProvider;
import com.invokingmachines.multistorage.meta.dto.MetaRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final MetaProvider metaProvider;
    private final QueryCompiler queryCompiler;
    private final QueryExecutionService queryExecutionService;

    public List<Map<String, Object>> search(String target, Query query) {
        QueryMeta meta = metaProvider.getMeta(MetaRequest.builder().build());
        CompiledQuery compiled = queryCompiler.compile(query, meta, target);
        List<Map<String, Object>> rows = queryExecutionService.execute(compiled);
        return ResultNestingTransformer.nestRelationFields(rows, compiled.getExpandedSelect(), meta, target);
    }
}
