package com.invokingmachines.multistorage.repository;

import com.invokingmachines.multistorage.entity.MetaTableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetaTableRepository extends JpaRepository<MetaTableEntity, UUID> {

    Optional<MetaTableEntity> findByName(String name);

    Optional<MetaTableEntity> findByAlias(String alias);

    @org.springframework.data.jpa.repository.Query("SELECT t FROM MetaTableEntity t WHERE t.alias IS NOT NULL AND TRIM(t.alias) != ''")
    List<MetaTableEntity> findAllWithNonBlankAlias();
}
