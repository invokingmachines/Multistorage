package com.invokingmachines.multistorage.data.service;

import com.invokingmachines.multistorage.dto.api.MetaDiscoveryDto;
import com.invokingmachines.multistorage.entity.MetaColumnEntity;
import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.repository.MetaColumnRepository;
import com.invokingmachines.multistorage.repository.MetaRelationRepository;
import com.invokingmachines.multistorage.repository.MetaTableRepository;
import com.invokingmachines.multistorage.util.NamingUtils;
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
            String stripped = tableRef.strip();
            return metaTableRepository.findByName(stripped)
                    .or(() -> metaTableRepository.findByAlias(stripped))
                    .or(() -> metaTableRepository.findByAlias(NamingUtils.fromPathSegment(stripped)))
                    .stream();
        }
        return metaTableRepository.findAllWithNonBlankAlias().stream();
    }

    private MetaDiscoveryDto.TableDiscoveryDto toTableDiscovery(MetaTableEntity t) {
        List<MetaColumnEntity> columnEntities = metaColumnRepository.findByTableId(t.getId());
        List<String> relations = metaRelationRepository.findByFromTableIdAndActiveTrue(t.getId()).stream()
                .map(MetaRelationEntity::getAlias)
                .toList();
        List<MetaDiscoveryDto.ColumnDiscoveryDto> columns = columnEntities.stream()
                .filter(c -> !Boolean.FALSE.equals(c.getReadable()))
                .map(this::toColumnDiscovery)
                .toList();
        return MetaDiscoveryDto.TableDiscoveryDto.builder()
                .alias(t.getAlias())
                .pathSegment(NamingUtils.toPathSegment(t.getAlias()))
                .name(t.getName())
                .relations(relations)
                .columns(columns)
                .build();
    }

    private MetaDiscoveryDto.ColumnDiscoveryDto toColumnDiscovery(MetaColumnEntity c) {
        return MetaDiscoveryDto.ColumnDiscoveryDto.builder()
                .alias(c.getAlias())
                .name(c.getName())
                .dataType(c.getDataType())
                .searchable(c.getSearchable())
                .editable(c.getEditable())
                .build();
    }
}
