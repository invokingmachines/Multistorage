package com.invokingmachines.multistorage.meta.service;

import com.invokingmachines.multistorage.dto.db.Column;
import com.invokingmachines.multistorage.dto.db.Relation;
import com.invokingmachines.multistorage.dto.db.Table;
import com.invokingmachines.multistorage.entity.MetaColumnEntity;
import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.repository.MetaColumnRepository;
import com.invokingmachines.multistorage.repository.MetaRelationRepository;
import com.invokingmachines.multistorage.config.MultistorageScanProperties;
import com.invokingmachines.multistorage.repository.MetaTableRepository;
import com.invokingmachines.multistorage.util.NamingUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetaSyncService {

    private final MetaTableRepository metaTableRepository;
    private final MetaColumnRepository metaColumnRepository;
    private final MetaRelationRepository metaRelationRepository;
    private final MultistorageScanProperties scanProperties;

    @Transactional
    public void syncFromScan(Map<String, Table> scanned) {
        scanned.values().forEach(this::syncTable);
        scanned.values().forEach(this::syncRelations);
    }

    private void syncTable(Table t) {
        MetaTableEntity entity = metaTableRepository.findByName(t.getName())
                .orElseGet(() -> {
                    MetaTableEntity e = MetaTableEntity.builder()
                            .name(t.getName())
                            .alias(NamingUtils.toPascalCase(t.getName()))
                            .build();
                    return metaTableRepository.save(e);
                });
        if (t.getColumns() != null) {
            for (Column c : t.getColumns()) {
                metaColumnRepository.findByTableIdAndName(entity.getId(), c.getName())
                        .map(existing -> {
                            existing.setDataType(c.getType());
                            return metaColumnRepository.save(existing);
                        })
                        .orElseGet(() -> metaColumnRepository.save(MetaColumnEntity.builder()
                                .table(entity)
                                .name(c.getName())
                                .alias(c.getName())
                                .dataType(c.getType())
                                .readable(true)
                                .searchable(true)
                                .editable(scanProperties.defaultEditableForNewColumn(c.getName(), c.getName()))
                                .build()));
            }
        }
    }

    private void syncRelations(Table childTable) {
        if (childTable.getRelations() == null) return;
        MetaTableEntity manyTableMeta = metaTableRepository.findByName(childTable.getName()).orElse(null);
        if (manyTableMeta == null) return;
        for (Relation fk : childTable.getRelations()) {
            MetaTableEntity oneTableMeta = metaTableRepository.findByName(fk.getReferencedTable()).orElse(null);
            if (oneTableMeta == null) continue;
            String parentToChildPascal = oneTableMeta.getAlias() + "To" + StringUtils.capitalize(manyTableMeta.getAlias());
            String childToParentPascal = manyTableMeta.getAlias() + "To" + StringUtils.capitalize(oneTableMeta.getAlias());

            saveOrUpdateRelation(oneTableMeta, manyTableMeta, fk.getReferencedColumn(), fk.getForeignKeyColumn(), true, NamingUtils.uncapitalize(parentToChildPascal));
            saveOrUpdateRelation(manyTableMeta, oneTableMeta, fk.getForeignKeyColumn(), fk.getReferencedColumn(), false, NamingUtils.uncapitalize(childToParentPascal));
        }
    }

    private void saveOrUpdateRelation(MetaTableEntity fromTable, MetaTableEntity toTable, String fromColumn, String toColumn, boolean oneToMany, String alias) {
        metaRelationRepository.findByFromTableIdAndAliasAndActiveTrue(fromTable.getId(), alias)
                .map(existing -> {
                    existing.setToTable(toTable);
                    existing.setFromColumn(fromColumn);
                    existing.setToColumn(toColumn);
                    existing.setOneToMany(oneToMany);
                    return metaRelationRepository.save(existing);
                })
                .orElseGet(() -> metaRelationRepository.save(MetaRelationEntity.builder()
                        .fromTable(fromTable)
                        .toTable(toTable)
                        .fromColumn(fromColumn)
                        .toColumn(toColumn)
                        .oneToMany(oneToMany)
                        .alias(alias)
                        .active(true)
                        .build()));
    }
}
