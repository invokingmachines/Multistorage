package com.invokingmachines.multistorage.meta.service;

import com.invokingmachines.multistorage.dto.api.MetaFeatureDto;
import com.invokingmachines.multistorage.entity.MetaFeatureEntity;
import com.invokingmachines.multistorage.liquibase.LiquibaseSchemaTarget;
import com.invokingmachines.multistorage.liquibase.LiquibaseTargetSchemaProvider;
import com.invokingmachines.multistorage.liquibase.SchemaLiquibaseRunner;
import com.invokingmachines.multistorage.repository.MetaFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MetaFeatureService {

    private final MetaFeatureRepository repository;
    private final DataSource dataSource;
    private final ObjectProvider<LiquibaseTargetSchemaProvider> liquibaseTargetSchemaProvider;

    @Transactional(readOnly = true)
    public List<MetaFeatureDto> findAll() {
        return repository.findAllByOrderByCodeAsc().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional
    public void enableByCode(String code) {
        LiquibaseTargetSchemaProvider provider = liquibaseTargetSchemaProvider.getIfAvailable();
        if (provider == null) {
            throw new IllegalStateException("LiquibaseTargetSchemaProvider is not configured");
        }
        LiquibaseSchemaTarget target = provider.currentTarget()
                .orElseThrow(() -> new IllegalStateException("Liquibase target schema is not available (e.g. tenant context missing)"));

        MetaFeatureEntity row = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown feature code: " + code));
        if (Boolean.TRUE.equals(row.getEnabled())) {
            return;
        }

        try {
            SchemaLiquibaseRunner.run(
                    dataSource,
                    row.getPath(),
                    target.defaultSchemaName(),
                    target.liquibaseSchemaName());
        } catch (Exception e) {
            throw new IllegalStateException("Liquibase update failed for feature " + code + ": " + e.getMessage(), e);
        }

        row.setEnabled(true);
        repository.save(row);
    }

    private MetaFeatureDto toDto(MetaFeatureEntity e) {
        return MetaFeatureDto.builder()
                .id(e.getId())
                .code(e.getCode())
                .path(e.getPath())
                .enabled(Boolean.TRUE.equals(e.getEnabled()))
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
