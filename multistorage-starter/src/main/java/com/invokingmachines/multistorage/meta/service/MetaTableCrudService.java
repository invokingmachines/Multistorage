package com.invokingmachines.multistorage.meta.service;

import com.invokingmachines.multistorage.dto.api.MetaTableDto;
import com.invokingmachines.multistorage.dto.api.MetaTableRequest;
import com.invokingmachines.multistorage.entity.MetaTableEntity;
import com.invokingmachines.multistorage.repository.MetaTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaTableCrudService {

    private final MetaTableRepository repository;

    public List<MetaTableDto> findAll() {
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    public MetaTableDto getByName(String name) {
        return repository.findByName(name).map(this::toDto).orElseThrow();
    }

    public MetaTableDto getByAlias(String alias) {
        return repository.findByAlias(alias).map(this::toDto).orElseThrow();
    }

    @Transactional
    public MetaTableDto upsert(MetaTableRequest request) {
        return repository.findByName(request.getName())
                .map(e -> {
                    if (request.getAlias() != null) e.setAlias(request.getAlias());
                    return toDto(repository.save(e));
                })
                .orElseGet(() -> toDto(repository.save(MetaTableEntity.builder()
                        .name(request.getName())
                        .alias(request.getAlias() != null ? request.getAlias() : request.getName())
                        .build())));
    }

    @Transactional
    public void deleteByName(String name) {
        repository.delete(repository.findByName(name).orElseThrow());
    }

    @Transactional
    public void deleteByAlias(String alias) {
        repository.delete(repository.findByAlias(alias).orElseThrow());
    }

    public Optional<MetaTableEntity> resolveTable(String nameOrAlias) {
        return repository.findByName(nameOrAlias).or(() -> repository.findByAlias(nameOrAlias));
    }

    private MetaTableDto toDto(MetaTableEntity e) {
        return MetaTableDto.builder()
                .name(e.getName())
                .alias(e.getAlias())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
