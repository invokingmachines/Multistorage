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
        if (request.getId() != null) {
            MetaTableEntity existing = repository.findById(request.getId()).orElseThrow();
            if (request.getName() != null) {
                existing.setName(request.getName());
            }
            if (request.getAlias() != null) {
                validateAliasNotConflictingWithName(request.getAlias(), "table", name ->
                        repository.findByName(name).filter(t -> !t.getId().equals(existing.getId())).isPresent());
                existing.setAlias(request.getAlias());
            }
            return toDto(repository.save(existing));
        }
        String proposedAlias = request.getAlias() != null ? request.getAlias() : request.getName();
        if (request.getAlias() != null || repository.findByName(request.getName()).isEmpty()) {
            validateAliasNotConflictingWithName(proposedAlias, "table", name -> repository.findByName(name).isPresent());
        }
        return repository.findByName(request.getName())
                .map(e -> {
                    if (request.getAlias() != null) e.setAlias(request.getAlias());
                    return toDto(repository.save(e));
                })
                .orElseGet(() -> toDto(repository.save(MetaTableEntity.builder()
                        .name(request.getName())
                        .alias(proposedAlias)
                        .build())));
    }

    private void validateAliasNotConflictingWithName(String proposedAlias, String entityType,
                                                    java.util.function.Predicate<String> nameExists) {
        if (proposedAlias == null || proposedAlias.isBlank()) return;
        if (nameExists.test(proposedAlias)) {
            throw new IllegalArgumentException(
                    "Alias '" + proposedAlias + "' conflicts with existing " + entityType + " name. Alias must be unique among names and aliases.");
        }
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
                .id(e.getId())
                .name(e.getName())
                .alias(e.getAlias())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
