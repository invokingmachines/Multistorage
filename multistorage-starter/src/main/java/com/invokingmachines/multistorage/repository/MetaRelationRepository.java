package com.invokingmachines.multistorage.repository;

import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetaRelationRepository extends JpaRepository<MetaRelationEntity, UUID> {

    List<MetaRelationEntity> findByManyTableId(UUID manyTableId);

    List<MetaRelationEntity> findByManyTableIdAndActiveTrue(UUID manyTableId);

    List<MetaRelationEntity> findByOneTableIdAndActiveTrue(UUID oneTableId);

    Optional<MetaRelationEntity> findByManyTableIdAndName(UUID manyTableId, String name);
}
