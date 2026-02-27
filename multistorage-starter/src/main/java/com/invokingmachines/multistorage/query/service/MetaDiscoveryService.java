package com.invokingmachines.multistorage.query.service;

import com.invokingmachines.multistorage.dto.api.MetaDiscoveryDto;
import com.invokingmachines.multistorage.entity.MetaColumnEntity;
import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.repository.MetaColumnRepository;
import com.invokingmachines.multistorage.repository.MetaRelationRepository;
import com.invokingmachines.multistorage.repository.MetaTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MetaDiscoveryService {

    private final MetaTableRepository metaTableRepository;
    private final MetaColumnRepository metaColumnRepository;
    private final MetaRelationRepository metaRelationRepository;

    public MetaDiscoveryDto getDiscovery(String tableRef) {
        Stream<MetaTableEntity> tableStream = streamTables(tableRef);
        List<MetaDiscoveryDto.TableDiscoveryDto> tables = tableStream
                .map(this::toTableDiscovery)
                .toList();
        return MetaDiscoveryDto.builder().tables(tables).build();
    }

    private Stream<MetaTableEntity> streamTables(String tableRef) {
        if (tableRef != null && !tableRef.isBlank()) {
            return metaTableRepository.findByName(tableRef.strip())
                    .or(() -> metaTableRepository.findByAlias(tableRef.strip()))
                    .stream();
        }
        return metaTableRepository.findAllWithNonBlankAlias().stream();
    }

    private MetaDiscoveryDto.TableDiscoveryDto toTableDiscovery(MetaTableEntity t) {
        List<MetaColumnEntity> columns = metaColumnRepository.findByTableId(t.getId());
        List<String> relations = metaRelationRepository.findByOneTableIdAndActiveTrue(t.getId()).stream()
                .map(MetaRelationEntity::getName)
                .toList();
        List<MetaDiscoveryDto.ColumnDiscoveryDto> selectable = columns.stream()
                .filter(c -> Boolean.TRUE.equals(c.getReadable()))
                .map(this::toColumnDiscovery)
                .toList();
        List<MetaDiscoveryDto.ColumnDiscoveryDto> searchable = columns.stream()
                .filter(c -> Boolean.TRUE.equals(c.getSearchable()))
                .map(this::toColumnDiscovery)
                .toList();
        return MetaDiscoveryDto.TableDiscoveryDto.builder()
                .alias(t.getAlias())
                .name(t.getName())
                .relations(relations)
                .selectableColumns(selectable)
                .searchableColumns(searchable)
                .build();
    }

    private MetaDiscoveryDto.ColumnDiscoveryDto toColumnDiscovery(MetaColumnEntity c) {
        return MetaDiscoveryDto.ColumnDiscoveryDto.builder()
                .alias(c.getAlias())
                .name(c.getName())
                .dataType(c.getDataType())
                .build();
    }
}
