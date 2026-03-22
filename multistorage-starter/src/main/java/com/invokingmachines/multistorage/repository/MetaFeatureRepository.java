package com.invokingmachines.multistorage.repository;

import com.invokingmachines.multistorage.entity.MetaFeatureEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetaFeatureRepository extends JpaRepository<MetaFeatureEntity, Long> {

    Optional<MetaFeatureEntity> findByCode(String code);

    List<MetaFeatureEntity> findAllByOrderByCodeAsc();
}
