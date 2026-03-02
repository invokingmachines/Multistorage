package com.invokingmachines.multistorage.meta.service;

import com.invokingmachines.multistorage.dto.api.MetaColumnDto;
import com.invokingmachines.multistorage.dto.api.MetaColumnRequest;
import com.invokingmachines.multistorage.entity.MetaColumnEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.repository.MetaColumnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaColumnCrudService {

    private final MetaColumnRepository repository;
    private final MetaTableCrudService metaTableCrudService;

    public List<MetaColumnDto> findByTable(String tableRef) {
        MetaTableEntity table = metaTableCrudService.resolveTable(tableRef).orElseThrow();
        return repository.findByTableId(table.getId()).stream()
                .map(e -> toDto(e, table))
                .collect(Collectors.toList());
    }

    public MetaColumnDto getByTableAndColumn(String tableRef, String columnRef) {
        MetaTableEntity table = metaTableCrudService.resolveTable(tableRef).orElseThrow();
        MetaColumnEntity col = resolveColumn(table.getId(), columnRef).orElseThrow();
        return toDto(col, table);
    }

    @Transactional
    public MetaColumnDto upsert(String tableRef, MetaColumnRequest request) {
        String ref = tableRef != null ? tableRef : request.getTable();
        MetaTableEntity table = metaTableCrudService.resolveTable(ref).orElseThrow();
        String proposedAlias = request.getAlias() != null ? request.getAlias() : request.getName();
        boolean isUpdate = repository.findByTableIdAndName(table.getId(), request.getName()).isPresent();
        if (request.getAlias() != null || !isUpdate) {
            validateColumnAliasNotConflictingWithName(table.getId(), proposedAlias);
        }
        return repository.findByTableIdAndName(table.getId(), request.getName())
                .map(e -> {
                    if (request.getAlias() != null) e.setAlias(request.getAlias());
                    if (request.getDataType() != null) e.setDataType(request.getDataType());
                    if (request.getReadable() != null) e.setReadable(request.getReadable());
                    if (request.getSearchable() != null) e.setSearchable(request.getSearchable());
                    return toDto(repository.save(e), table);
                })
                .orElseGet(() -> toDto(repository.save(MetaColumnEntity.builder()
                        .table(table)
                        .name(request.getName())
                        .alias(proposedAlias)
                        .dataType(request.getDataType())
                        .readable(request.getReadable() != null ? request.getReadable() : true)
                        .searchable(request.getSearchable() != null ? request.getSearchable() : true)
                        .build()), table));
    }

    private void validateColumnAliasNotConflictingWithName(UUID tableId, String proposedAlias) {
        if (proposedAlias == null || proposedAlias.isBlank()) return;
        boolean nameConflict = repository.findByTableId(tableId).stream()
                .anyMatch(c -> proposedAlias.equals(c.getName()));
        if (nameConflict) {
            throw new IllegalArgumentException(
                    "Alias '" + proposedAlias + "' conflicts with existing column name. Alias must be unique among names and aliases.");
        }
    }

    @Transactional
    public void delete(String tableRef, String columnRef) {
        MetaTableEntity table = metaTableCrudService.resolveTable(tableRef).orElseThrow();
        MetaColumnEntity e = resolveColumn(table.getId(), columnRef).orElseThrow();
        repository.delete(e);
    }

    private Optional<MetaColumnEntity> resolveColumn(UUID tableId, String nameOrAlias) {
        return repository.findByTableIdAndName(tableId, nameOrAlias)
                .or(() -> repository.findByTableIdAndAlias(tableId, nameOrAlias));
    }

    private MetaColumnDto toDto(MetaColumnEntity e, MetaTableEntity table) {
        return MetaColumnDto.builder()
                .table(table.getAlias())
                .name(e.getName())
                .alias(e.getAlias())
                .dataType(e.getDataType())
                .readable(e.getReadable())
                .searchable(e.getSearchable())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
