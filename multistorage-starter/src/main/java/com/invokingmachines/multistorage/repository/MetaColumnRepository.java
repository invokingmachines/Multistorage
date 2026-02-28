package com.invokingmachines.multistorage.repository;

import com.invokingmachines.multistorage.entity.MetaColumnEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetaColumnRepository extends JpaRepository<MetaColumnEntity, UUID> {

    List<MetaColumnEntity> findByTableId(UUID tableId);

    Optional<MetaColumnEntity> findByTableIdAndName(UUID tableId, String name);

    Optional<MetaColumnEntity> findByTableIdAndAlias(UUID tableId, String alias);
}
