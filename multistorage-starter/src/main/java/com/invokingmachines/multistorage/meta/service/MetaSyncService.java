package com.invokingmachines.multistorage.meta.service;

import com.invokingmachines.multistorage.dto.db.Column;
import com.invokingmachines.multistorage.dto.db.Relation;
import com.invokingmachines.multistorage.dto.db.Table;
import com.invokingmachines.multistorage.entity.MetaColumnEntity;
import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.repository.MetaColumnRepository;
import com.invokingmachines.multistorage.repository.MetaRelationRepository;
import com.invokingmachines.multistorage.repository.MetaTableRepository;
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
                            .alias(t.getName())
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
            String defaultName = manyTableMeta.getAlias() + "To" + StringUtils.capitalize(oneTableMeta.getAlias());
            String defaultInverseName = oneTableMeta.getAlias() + "To" + StringUtils.capitalize(manyTableMeta.getAlias());
            metaRelationRepository.findByManyTableIdAndName(manyTableMeta.getId(), defaultName)
                    .map(existing -> {
                        existing.setManyColumn(fk.getForeignKeyColumn());
                        existing.setOneColumn(fk.getReferencedColumn());
                        existing.setInverseName(defaultInverseName);
                        return metaRelationRepository.save(existing);
                    })
                    .orElseGet(() -> metaRelationRepository.save(MetaRelationEntity.builder()
                            .manyTable(manyTableMeta)
                            .oneTable(oneTableMeta)
                            .manyColumn(fk.getForeignKeyColumn())
                            .oneColumn(fk.getReferencedColumn())
                            .name(defaultName)
                            .inverseName(defaultInverseName)
                            .active(true)
                            .build()));
        }
    }
}
