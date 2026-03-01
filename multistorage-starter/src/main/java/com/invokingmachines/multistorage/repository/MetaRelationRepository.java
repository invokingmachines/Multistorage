package com.invokingmachines.multistorage.repository;

import com.invokingmachines.multistorage.entity.MetaRelationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetaRelationRepository extends JpaRepository<MetaRelationEntity, UUID> {

    List<MetaRelationEntity> findByFromTableIdAndActiveTrue(UUID fromTableId);

    Optional<MetaRelationEntity> findByFromTableIdAndAliasAndActiveTrue(UUID fromTableId, String alias);
}
