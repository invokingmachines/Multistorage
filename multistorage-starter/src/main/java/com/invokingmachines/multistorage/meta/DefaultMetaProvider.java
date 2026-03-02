package com.invokingmachines.multistorage.meta;

import com.invokingmachines.multistorage.dto.meta.ColumnMeta;
import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.pipeline.CascadeType;
import com.invokingmachines.multistorage.entity.MetaColumnEntity;
import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.meta.dto.MetaRequest;
import com.invokingmachines.multistorage.repository.MetaColumnRepository;
import com.invokingmachines.multistorage.repository.MetaRelationRepository;
import com.invokingmachines.multistorage.repository.MetaTableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DefaultMetaProvider implements MetaProvider {

    private final MetaTableRepository metaTableRepository;
    private final MetaColumnRepository metaColumnRepository;
    private final MetaRelationRepository metaRelationRepository;
    private final List<MetaCustomizer> customizers;

    public DefaultMetaProvider(MetaTableRepository metaTableRepository,
                               MetaColumnRepository metaColumnRepository,
                               MetaRelationRepository metaRelationRepository,
                               @Autowired(required = false) List<MetaCustomizer> customizers) {
        this.metaTableRepository = metaTableRepository;
        this.metaColumnRepository = metaColumnRepository;
        this.metaRelationRepository = metaRelationRepository;
        this.customizers = customizers != null ? customizers : Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public QueryMeta getMeta(MetaRequest request) {
        QueryMeta meta = loadFromStore();
        return customizers.stream()
                .reduce(meta, (m, c) -> c.customize(m, request), (a, b) -> b);
    }

    private QueryMeta loadFromStore() {
        List<MetaTableEntity> tables = metaTableRepository.findAll();
        Map<String, TableMeta> tableMap = new LinkedHashMap<>();
        tables.stream()
                .filter(t -> t.getAlias() != null && !t.getAlias().isBlank())
                .forEach(t -> {
                    TableMeta tm = toTableMeta(t);
                    tableMap.put(t.getName(), tm);
                    if (!t.getName().equals(t.getAlias())) {
                        tableMap.put(t.getAlias(), tm);
                    }
                });
        return QueryMeta.builder()
                .tables(tableMap)
                .build();
    }

    private TableMeta toTableMeta(MetaTableEntity t) {
        String tableAlias = t.getAlias();
        Map<String, ColumnMeta> columns = new LinkedHashMap<>();
        metaColumnRepository.findByTableId(t.getId()).stream()
                .filter(c -> c.getAlias() != null && !c.getAlias().isBlank())
                .forEach(c -> {
                    ColumnMeta cm = toColumnMeta(c);
                    columns.put(c.getName(), cm);
                    if (!c.getName().equals(c.getAlias())) {
                        columns.put(c.getAlias(), cm);
                    }
                });
        Map<String, RelationMeta> relations = metaRelationRepository.findByFromTableIdAndActiveTrue(t.getId()).stream()
                .collect(Collectors.toMap(MetaRelationEntity::getAlias, this::toRelationMeta));
        return TableMeta.builder()
                .name(t.getName())
                .alias(tableAlias)
                .columns(columns)
                .relations(relations)
                .build();
    }

    private ColumnMeta toColumnMeta(MetaColumnEntity c) {
        return ColumnMeta.builder()
                .name(c.getName())
                .alias(c.getAlias())
                .dataType(c.getDataType())
                .build();
    }

    private RelationMeta toRelationMeta(MetaRelationEntity r) {
        CascadeType cascade = CascadeType.NONE;
        if (r.getCascadeType() != null && !r.getCascadeType().isBlank()) {
            try {
                cascade = CascadeType.valueOf(r.getCascadeType().toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        return RelationMeta.builder()
                .alias(r.getAlias())
                .fromTable(r.getFromTable().getName())
                .toTable(r.getToTable().getName())
                .fromColumn(r.getFromColumn())
                .toColumn(r.getToColumn())
                .oneToMany(Boolean.TRUE.equals(r.getOneToMany()))
                .cascade(cascade)
                .build();
    }
}
