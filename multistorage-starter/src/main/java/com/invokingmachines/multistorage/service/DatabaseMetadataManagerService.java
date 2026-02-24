package com.invokingmachines.multistorage.service;

import com.invokingmachines.multistorage.dto.db.Column;
import com.invokingmachines.multistorage.dto.db.Relation;
import com.invokingmachines.multistorage.dto.db.Table;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseMetadataManagerService {

    private final DataSource dataSource;
    private Map<String, Table> tablesMetadata = new HashMap<>();

    public Map<String, Table> scanDatabase() {
        tablesMetadata.clear();
        
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema() == null ? "public" : connection.getSchema();
            
            log.info("Scanning database: catalog={}, schema={}", catalog, schema);
            
            scanTables(metaData, catalog, schema);
            scanColumns(metaData, catalog, schema);
            scanRelations(metaData, catalog, schema);
            
            log.info("Database scan completed. Found {} tables", tablesMetadata.size());
            
        } catch (SQLException e) {
            log.error("Error scanning database", e);
            throw new RuntimeException("Failed to scan database", e);
        }
        
        return tablesMetadata;
    }

    private void scanTables(DatabaseMetaData metaData, String catalog, String schema) throws SQLException {
        try (ResultSet tables = metaData.getTables(catalog, schema, "%", new String[]{"TABLE", "VIEW"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                String tableType = tables.getString("TABLE_TYPE");
                String tableSchema = tables.getString("TABLE_SCHEM");
                String tableCatalog = tables.getString("TABLE_CAT");
                String remarks = tables.getString("REMARKS");
                
                Table table = Table.builder()
                        .name(tableName)
                        .schema(tableSchema)
                        .catalog(tableCatalog)
                        .type(tableType)
                        .remarks(remarks)
                        .columns(new ArrayList<>())
                        .relations(new ArrayList<>())
                        .build();
                
                tablesMetadata.put(tableName, table);
            }
        }
    }

    private void scanColumns(DatabaseMetaData metaData, String catalog, String schema) throws SQLException {
        for (Table table : tablesMetadata.values()) {
            try (ResultSet columns = metaData.getColumns(catalog, schema, table.getName(), "%")) {
                List<Column> columnList = new ArrayList<>();
                
                while (columns.next()) {
                    Column column = Column.builder()
                            .name(columns.getString("COLUMN_NAME"))
                            .type(columns.getString("TYPE_NAME"))
                            .size(columns.getInt("COLUMN_SIZE"))
                            .decimalDigits(columns.getInt("DECIMAL_DIGITS"))
                            .nullable(columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable)
                            .defaultValue(columns.getString("COLUMN_DEF"))
                            .remarks(columns.getString("REMARKS"))
                            .build();
                    
                    columnList.add(column);
                }
                
                table.setColumns(columnList);
            }
        }
        
        markPrimaryKeys(metaData, catalog, schema);
    }

    private void markPrimaryKeys(DatabaseMetaData metaData, String catalog, String schema) throws SQLException {
        for (Table table : tablesMetadata.values()) {
            try (ResultSet primaryKeys = metaData.getPrimaryKeys(catalog, schema, table.getName())) {
                Map<String, Boolean> pkMap = new HashMap<>();
                
                while (primaryKeys.next()) {
                    String columnName = primaryKeys.getString("COLUMN_NAME");
                    pkMap.put(columnName, true);
                }
                
                table.getColumns().forEach(column -> 
                    column.setPrimaryKey(pkMap.getOrDefault(column.getName(), false))
                );
            }
        }
    }

    private void scanRelations(DatabaseMetaData metaData, String catalog, String schema) throws SQLException {
        for (Table table : tablesMetadata.values()) {
            try (ResultSet foreignKeys = metaData.getImportedKeys(catalog, schema, table.getName())) {
                List<Relation> relations = new ArrayList<>();
                
                while (foreignKeys.next()) {
                    Relation relation = Relation.builder()
                            .name(foreignKeys.getString("FK_NAME"))
                            .foreignKeyColumn(foreignKeys.getString("FKCOLUMN_NAME"))
                            .referencedTable(foreignKeys.getString("PKTABLE_NAME"))
                            .referencedColumn(foreignKeys.getString("PKCOLUMN_NAME"))
                            .updateRule(foreignKeys.getString("UPDATE_RULE"))
                            .deleteRule(foreignKeys.getString("DELETE_RULE"))
                            .build();
                    
                    relations.add(relation);
                }
                
                table.setRelations(relations);
            }
        }
    }

    public Map<String, Table> getTablesMetadata() {
        return tablesMetadata;
    }
}
