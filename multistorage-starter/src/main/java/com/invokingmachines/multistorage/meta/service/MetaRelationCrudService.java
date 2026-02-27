package com.invokingmachines.multistorage.meta.service;

import com.invokingmachines.multistorage.dto.api.MetaRelationDto;
import com.invokingmachines.multistorage.dto.api.MetaRelationRequest;
import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.repository.MetaRelationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaRelationCrudService {

    private final MetaRelationRepository repository;
    private final MetaTableCrudService metaTableCrudService;

    public List<MetaRelationDto> findByManyTable(String manyTableRef) {
        MetaTableEntity manyTable = metaTableCrudService.resolveTable(manyTableRef).orElseThrow();
        return repository.findByManyTableId(manyTable.getId()).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public MetaRelationDto getByManyTableAndName(String manyTableRef, String relationName) {
        MetaTableEntity manyTable = metaTableCrudService.resolveTable(manyTableRef).orElseThrow();
        return repository.findByManyTableIdAndName(manyTable.getId(), relationName)
                .map(this::toDto)
                .orElseThrow();
    }

    @Transactional
    public MetaRelationDto upsert(MetaRelationRequest request) {
        MetaTableEntity manyTable = metaTableCrudService.resolveTable(request.getManyTable()).orElseThrow();
        MetaTableEntity oneTable = metaTableCrudService.resolveTable(request.getOneTable()).orElseThrow();
        return repository.findByManyTableIdAndName(manyTable.getId(), request.getName())
                .map(e -> {
                    e.setManyColumn(request.getManyColumn());
                    e.setOneColumn(request.getOneColumn());
                    if (request.getActive() != null) e.setActive(request.getActive());
                    if (request.getManyTable() != null) e.setManyTable(metaTableCrudService.resolveTable(request.getManyTable()).orElseThrow());
                    if (request.getOneTable() != null) e.setOneTable(metaTableCrudService.resolveTable(request.getOneTable()).orElseThrow());
                    return toDto(repository.save(e));
                })
                .orElseGet(() -> toDto(repository.save(MetaRelationEntity.builder()
                        .manyTable(manyTable)
                        .oneTable(oneTable)
                        .manyColumn(request.getManyColumn())
                        .oneColumn(request.getOneColumn())
                        .name(request.getName())
                        .active(request.getActive() != null ? request.getActive() : true)
                        .build())));
    }

    @Transactional
    public void delete(String manyTableRef, String relationName) {
        MetaTableEntity manyTable = metaTableCrudService.resolveTable(manyTableRef).orElseThrow();
        MetaRelationEntity e = repository.findByManyTableIdAndName(manyTable.getId(), relationName).orElseThrow();
        repository.delete(e);
    }

    private MetaRelationDto toDto(MetaRelationEntity e) {
        return MetaRelationDto.builder()
                .manyTable(e.getManyTable().getAlias())
                .oneTable(e.getOneTable().getAlias())
                .manyColumn(e.getManyColumn())
                .oneColumn(e.getOneColumn())
                .name(e.getName())
                .active(e.getActive())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
