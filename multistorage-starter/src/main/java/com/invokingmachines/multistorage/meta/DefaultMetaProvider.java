package com.invokingmachines.multistorage.meta;

import com.invokingmachines.multistorage.dto.meta.ColumnMeta;
import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.entity.MetaColumnEntity;
import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.meta.dto.MetaRequest;
import com.invokingmachines.multistorage.repository.MetaColumnRepository;
import com.invokingmachines.multistorage.repository.MetaRelationRepository;
import com.invokingmachines.multistorage.repository.MetaTableRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
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
        Map<String, TableMeta> tableMap = tables.stream()
                .filter(t -> t.getAlias() != null && !t.getAlias().isBlank())
                .collect(Collectors.toMap(MetaTableEntity::getAlias, this::toTableMeta));
        return QueryMeta.builder()
                .tables(tableMap)
                .build();
    }

    private TableMeta toTableMeta(MetaTableEntity t) {
        String tableAlias = t.getAlias();
        Map<String, ColumnMeta> columns = metaColumnRepository.findByTableId(t.getId()).stream()
                .filter(c -> c.getAlias() != null && !c.getAlias().isBlank())
                .collect(Collectors.toMap(MetaColumnEntity::getAlias, this::toColumnMeta));
        Map<String, RelationMeta> fromOne = metaRelationRepository.findByOneTableIdAndActiveTrue(t.getId()).stream()
                .collect(Collectors.toMap(r -> oneToManyRelationName(r), this::toRelationMetaFromOne));
        Map<String, RelationMeta> fromMany = metaRelationRepository.findByManyTableIdAndActiveTrue(t.getId()).stream()
                .collect(Collectors.toMap(MetaRelationEntity::getName, this::toRelationMetaFromMany));
        Map<String, RelationMeta> relations = new java.util.LinkedHashMap<>(fromOne);
        fromMany.forEach(relations::putIfAbsent);
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
                .readable(Boolean.TRUE.equals(c.getReadable()))
                .searchable(Boolean.TRUE.equals(c.getSearchable()))
                .build();
    }

    private static String oneToManyRelationName(MetaRelationEntity r) {
        return r.getOneTable().getAlias() + "To" + StringUtils.capitalize(r.getManyTable().getAlias());
    }

    private RelationMeta toRelationMetaFromOne(MetaRelationEntity r) {
        return RelationMeta.builder()
                .name(oneToManyRelationName(r))
                .childTable(r.getManyTable().getName())
                .manyColumn(r.getManyColumn())
                .oneColumn(r.getOneColumn())
                .joinCurrentColumn(r.getOneColumn())
                .joinChildColumn(r.getManyColumn())
                .build();
    }

    private RelationMeta toRelationMetaFromMany(MetaRelationEntity r) {
        return RelationMeta.builder()
                .name(r.getName())
                .childTable(r.getOneTable().getName())
                .manyColumn(r.getManyColumn())
                .oneColumn(r.getOneColumn())
                .joinCurrentColumn(r.getManyColumn())
                .joinChildColumn(r.getOneColumn())
                .build();
    }
}
