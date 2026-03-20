package com.invokingmachines.multistorage.meta.service;

import com.invokingmachines.multistorage.dto.api.MetaRelationDto;
import com.invokingmachines.multistorage.dto.api.MetaRelationRequest;
import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.repository.MetaColumnRepository;
import com.invokingmachines.multistorage.repository.MetaRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaRelationCrudService {

    private final MetaRelationRepository repository;
    private final MetaTableCrudService metaTableCrudService;
    private final MetaColumnRepository metaColumnRepository;

    public List<MetaRelationDto> findByFromTable(String fromTableRef) {
        MetaTableEntity fromTable = metaTableCrudService.resolveTable(fromTableRef).orElseThrow();
        return repository.findByFromTableIdAndActiveTrue(fromTable.getId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public MetaRelationDto getByFromTableAndAlias(String fromTableRef, String alias) {
        MetaTableEntity fromTable = metaTableCrudService.resolveTable(fromTableRef).orElseThrow();
        return repository.findByFromTableIdAndAliasAndActiveTrue(fromTable.getId(), alias)
                .map(this::toDto)
                .orElseThrow();
    }

    @Transactional
    public MetaRelationDto upsert(MetaRelationRequest request) {
        MetaTableEntity fromTable = metaTableCrudService.resolveTable(request.getFromTable()).orElseThrow();
        MetaTableEntity toTable = metaTableCrudService.resolveTable(request.getToTable()).orElseThrow();
        if (request.getId() != null) {
            MetaRelationEntity existing = repository.findById(request.getId()).orElseThrow();
            existing.setFromTable(fromTable);
            existing.setToTable(toTable);
            if (request.getAlias() != null) {
                validateRelationAliasNotConflictingWithColumnName(fromTable.getId(), request.getAlias());
                existing.setAlias(request.getAlias());
            }
            if (request.getFromColumn() != null) existing.setFromColumn(request.getFromColumn());
            if (request.getToColumn() != null) existing.setToColumn(request.getToColumn());
            if (request.getOneToMany() != null) existing.setOneToMany(request.getOneToMany());
            if (request.getCascadeType() != null) existing.setCascadeType(request.getCascadeType());
            if (request.getActive() != null) existing.setActive(request.getActive());
            return toDto(repository.save(existing));
        }
        if (request.getAlias() != null) {
            validateRelationAliasNotConflictingWithColumnName(fromTable.getId(), request.getAlias());
        }
        return repository.findByFromTableIdAndAliasAndActiveTrue(fromTable.getId(), request.getAlias())
                .map(e -> {
                    e.setToTable(toTable);
                    e.setFromColumn(request.getFromColumn());
                    e.setToColumn(request.getToColumn());
                    e.setOneToMany(request.getOneToMany() != null ? request.getOneToMany() : true);
                    if (request.getCascadeType() != null) e.setCascadeType(request.getCascadeType());
                    if (request.getActive() != null) e.setActive(request.getActive());
                    return toDto(repository.save(e));
                })
                .orElseGet(() -> toDto(repository.save(MetaRelationEntity.builder()
                        .fromTable(fromTable)
                        .toTable(toTable)
                        .fromColumn(request.getFromColumn())
                        .toColumn(request.getToColumn())
                        .oneToMany(request.getOneToMany() != null ? request.getOneToMany() : true)
                        .alias(request.getAlias())
                        .cascadeType(request.getCascadeType() != null ? request.getCascadeType() : "NONE")
                        .active(request.getActive() != null ? request.getActive() : true)
                        .build())));
    }

    @Transactional
    public void delete(String fromTableRef, String alias) {
        MetaTableEntity fromTable = metaTableCrudService.resolveTable(fromTableRef).orElseThrow();
        MetaRelationEntity e = repository.findByFromTableIdAndAliasAndActiveTrue(fromTable.getId(), alias).orElseThrow();
        repository.delete(e);
    }

    private void validateRelationAliasNotConflictingWithColumnName(UUID fromTableId, String proposedAlias) {
        if (proposedAlias == null || proposedAlias.isBlank()) return;
        boolean nameConflict = metaColumnRepository.findByTableId(fromTableId).stream()
                .anyMatch(c -> proposedAlias.equals(c.getName()));
        if (nameConflict) {
            throw new IllegalArgumentException(
                    "Relation alias '" + proposedAlias + "' conflicts with existing column name. Alias must be unique among names and aliases.");
        }
    }

    private MetaRelationDto toDto(MetaRelationEntity e) {
        return MetaRelationDto.builder()
                .id(e.getId())
                .fromTable(e.getFromTable().getAlias())
                .toTable(e.getToTable().getAlias())
                .fromColumn(e.getFromColumn())
                .toColumn(e.getToColumn())
                .oneToMany(Boolean.TRUE.equals(e.getOneToMany()))
                .alias(e.getAlias())
                .cascadeType(e.getCascadeType())
                .active(e.getActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
