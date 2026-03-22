package com.invokingmachines.multistorage.liquibase;

import java.util.Optional;

@FunctionalInterface
public interface LiquibaseTargetSchemaProvider {

    Optional<LiquibaseSchemaTarget> currentTarget();
}
