package com.invokingmachines.multistorage.liquibase;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import javax.sql.DataSource;
import java.sql.Connection;

public final class SchemaLiquibaseRunner {

    private SchemaLiquibaseRunner() {
    }

    public static void run(DataSource dataSource, String changelogClasspath, String defaultSchema, String liquibaseSchema)
            throws Exception {
        String path = changelogClasspath.startsWith("classpath:")
                ? changelogClasspath.substring("classpath:".length())
                : changelogClasspath;
        try (Connection connection = dataSource.getConnection()) {
            JdbcConnection jdbcConnection = new JdbcConnection(connection);
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(jdbcConnection);
            database.setDefaultSchemaName(defaultSchema);
            database.setLiquibaseSchemaName(liquibaseSchema);
            Liquibase liquibase = new Liquibase(path, new ClassLoaderResourceAccessor(), database);
            liquibase.update("");
        }
    }
}
