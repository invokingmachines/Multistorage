package com.invokingmachines.multistorage.query.service;

import com.invokingmachines.multistorage.query.dto.CompiledQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QueryExecutionService {

    private final JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> execute(CompiledQuery query) {
        return jdbcTemplate.queryForList(query.getSql(), query.getParameters().toArray());
    }
}
