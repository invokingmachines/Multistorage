package com.invokingmachines.multistorage.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invokingmachines.multistorage.dto.meta.ColumnMeta;
import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.RelationMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.query.dto.CompiledQuery;
import com.invokingmachines.multistorage.query.service.MetaAliasMapper;
import com.invokingmachines.multistorage.query.service.QueryCompiler;
import com.invokingmachines.multistorage.query.service.ValueConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcQueryCompilerTest {

    private ObjectMapper objectMapper;
    private QueryCompiler compiler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        compiler = new QueryCompiler(new MetaAliasMapper(), new ValueConverter());
    }

    @Test
    void jsonToSql_simpleEq() throws Exception {
        QueryMeta qm = QueryMeta.builder()
                .table("parent_db",
                        TableMeta.builder()
                                .alias("parent")
                                .name("parent_db")
                                .column("name_db",
                                        ColumnMeta.builder()
                                                .name("name_db")
                                                .alias("name")
                                                .dataType("varchar")
                                                .build())

                                .relation("children",
                                        RelationMeta.builder()
                                                .alias("children")
                                                .fromTable("parent_db")
                                                .toTable("child_db")
                                                .fromColumn("id")
                                                .toColumn("parent_id")
                                                .oneToMany(true)
                                                .build())
                                .build())
                .table("child_db",
                        TableMeta.builder()
                                .alias("child")
                                .name("child_db")
                                .column("name_db",
                                        ColumnMeta.builder()
                                                .name("name_db")
                                                .alias("name")
                                                .dataType("varchar")
                                                .build())
                                .column("parent_id",
                                        ColumnMeta.builder()
                                                .name("parent_id")
                                                .alias("parent_id")
                                                .dataType("int8")
                                                .build())
                                .build())
                .build();

        String json = """
{
    "select":[["name"], ["children", "name"]],
    "where":{
        "logician":"AND",
        "criteria":[
            {"field":["children", "name"],"operator":"EQ","value":"test"},
            {
                "logician":"AND",
                "criteria":[
                    {"field":["children", "name"],"operator":"EQ","value":"test"},
                    {"field":["name"],"operator":"EQ","value":"test"}
                ]
            }
            ]
        }
}
                """;
        Query query = objectMapper.readValue(json, Query.class);
        CompiledQuery result = compiler.compile(query, qm, "parent");

        assertThat(result.getSql()).contains("SELECT");
        assertThat(result.getSql()).contains("\"parent_db\"");
        assertThat(result.getSql()).contains("\"name\"");
        assertThat(result.getSql()).contains("=");
        assertThat(result.getParameters()).containsExactly("test", "test", "test");
    }
//
//    @Test
//    void jsonToSql_selectColumns() throws Exception {
//        String json = """
//                {   "select":[["id"],["name"]],
//                    "where":{"logician":"AND","criteria":[{"field":["id"],"operator":"GT","value":10}]}
//                }
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("\"id\"");
//        assertThat(result.getSql()).contains("\"name\"");
//        assertThat(result.getSql()).contains(">");
//        assertThat(result.getParameters()).containsExactly(10);
//    }
//
//    @Test
//    void jsonToSql_andOr() throws Exception {
//        String json = """
//                {"where":{"logician":"OR","criteria":[{"logician":"AND","criteria":[{"field":["name"],"operator":"EQ","value":"a"},{"field":["value"],"operator":"EQ","value":1}]},{"field":["name"],"operator":"EQ","value":"b"}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("OR");
//        assertThat(result.getSql()).contains("AND");
//        assertThat(result.getParameters()).containsExactly("a", 1, "b");
//    }
//
//    @Test
//    void jsonToSql_in() throws Exception {
//        String json = """
//                {"where":{"logician":"AND","criteria":[{"field":["id"],"operator":"IN","value":[1,2,3]}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("IN (?, ?, ?)");
//        assertThat(result.getParameters()).containsExactly(1, 2, 3);
//    }
//
//    @Test
//    void jsonToSql_likeAndNull() throws Exception {
//        String json = """
//                {"where":{"logician":"AND","criteria":[{"field":["name"],"operator":"LIKE","value":"%x%"},{"field":["description"],"operator":"NOT_NULL"}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LIKE ?");
//        assertThat(result.getSql()).contains("IS NOT NULL");
//        assertThat(result.getParameters()).containsExactly("%x%");
//    }
//
//    @Test
//    void jsonToSql_between() throws Exception {
//        String json = """
//                {"where":{"logician":"AND","criteria":[{"field":["value"],"operator":"BETWEEN","value":[10,20]}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("BETWEEN ? AND ?");
//        assertThat(result.getParameters()).containsExactly(10, 20);
//    }
//
//    @Test
//    void jsonToSql_noWhere() throws Exception {
//        String json = "{}";
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).isEqualTo("SELECT * FROM \"parent\"");
//        assertThat(result.getParameters()).isEmpty();
//    }
//
//    @Test
//    void jsonToSql_withJoin_parent_children_select() throws Exception {
//        EntitySchema schema = createParentChildSchema();
//
//        String json = """
//                {"select":[["id"],["children","name"]]}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LEFT JOIN");
//        assertThat(result.getSql()).contains("\"child\"");
//        assertThat(result.getSql()).contains("ON \"parent\".\"id\" = \"children\".\"parent_id\"");
//        assertThat(result.getSql()).contains("\"parent\".\"id\"");
//        assertThat(result.getSql()).contains("\"children\".\"name\"");
//        assertThat(result.getParameters()).isEmpty();
//    }
//
//    @Test
//    void jsonToSql_withJoin_parent_children_where() throws Exception {
//        EntitySchema schema = createParentChildSchema();
//
//        String json = """
//                {"where":{"logician":"AND","criteria":[{"field":["children","value"],"operator":"GT","value":5}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LEFT JOIN");
//        assertThat(result.getSql()).contains("\"child\"");
//        assertThat(result.getSql()).contains("ON \"parent\".\"id\" = \"children\".\"parent_id\"");
//        assertThat(result.getSql()).contains("\"children\".\"value\"");
//        assertThat(result.getSql()).contains("> ?");
//        assertThat(result.getParameters()).containsExactly(5);
//    }
//
//    @Test
//    void jsonToSql_withJoin_parent_children_selectAndWhere() throws Exception {
//        EntitySchema schema = createParentChildSchema();
//
//        String json = """
//                {"select":[["id"],["children","name"]],"where":{"logician":"AND","criteria":[{"field":["children","value"],"operator":"GT","value":5}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LEFT JOIN");
//        assertThat(result.getSql()).contains("\"child\"");
//        assertThat(result.getSql()).contains("ON \"parent\".\"id\" = \"children\".\"parent_id\"");
//        assertThat(result.getSql()).contains("\"parent\".\"id\"");
//        assertThat(result.getSql()).contains("\"children\".\"name\"");
//        assertThat(result.getSql()).contains("\"children\".\"value\"");
//        assertThat(result.getParameters()).containsExactly(5);
//    }
//
//    @Test
//    void jsonToSql_withJoin_multipleConditions() throws Exception {
//        EntitySchema schema = createParentChildSchema();
//
//        String json = """
//                {"where":{"logician":"AND","criteria":[{"field":["name"],"operator":"EQ","value":"test"},{"field":["children","value"],"operator":"GT","value":10}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LEFT JOIN");
//        assertThat(result.getSql()).contains("\"parent\".\"name\"");
//        assertThat(result.getSql()).contains("\"children\".\"value\"");
//        assertThat(result.getSql()).contains("AND");
//        assertThat(result.getParameters()).containsExactly("test", 10);
//    }
//
//    @Test
//    void jsonToSql_withJoin_orCondition() throws Exception {
//        EntitySchema schema = createParentChildSchema();
//
//        String json = """
//                {"where":{"logician":"OR","criteria":[{"field":["name"],"operator":"EQ","value":"a"},{"field":["children","name"],"operator":"EQ","value":"b"}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LEFT JOIN");
//        assertThat(result.getSql()).contains("OR");
//        assertThat(result.getSql()).contains("\"parent\".\"name\"");
//        assertThat(result.getSql()).contains("\"children\".\"name\"");
//        assertThat(result.getParameters()).containsExactly("a", "b");
//    }
//
//    @Test
//    void jsonToSql_withJoin_inCondition() throws Exception {
//        EntitySchema schema = createParentChildSchema();
//
//        String json = """
//                {"where":{"logician":"AND","criteria":[{"field":["children","id"],"operator":"IN","value":[1,2,3]}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LEFT JOIN");
//        assertThat(result.getSql()).contains("\"children\".\"id\"");
//        assertThat(result.getSql()).contains("IN (?, ?, ?)");
//        assertThat(result.getParameters()).containsExactly(1, 2, 3);
//    }
//
//    @Test
//    void jsonToSql_withJoin_likeCondition() throws Exception {
//        EntitySchema schema = createParentChildSchema();
//
//        String json = """
//                {"where":{"logician":"AND","criteria":[{"field":["children","name"],"operator":"LIKE","value":"%test%"}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LEFT JOIN");
//        assertThat(result.getSql()).contains("\"children\".\"name\"");
//        assertThat(result.getSql()).contains("LIKE ?");
//        assertThat(result.getParameters()).containsExactly("%test%");
//    }
//
//    @Test
//    void jsonToSql_withJoin_nullCheck() throws Exception {
//        EntitySchema schema = createParentChildSchema();
//
//        String json = """
//                {"where":{"logician":"AND","criteria":[{"field":["children","value"],"operator":"NOT_NULL"}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LEFT JOIN");
//        assertThat(result.getSql()).contains("\"children\".\"value\"");
//        assertThat(result.getSql()).contains("IS NOT NULL");
//        assertThat(result.getParameters()).isEmpty();
//    }
//
//    @Test
//    void jsonToSql_withJoin_betweenCondition() throws Exception {
//        EntitySchema schema = createParentChildSchema();
//
//        String json = """
//                {"where":{"logician":"AND","criteria":[{"field":["children","value"],"operator":"BETWEEN","value":[5,15]}]}}
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LEFT JOIN");
//        assertThat(result.getSql()).contains("\"children\".\"value\"");
//        assertThat(result.getSql()).contains("BETWEEN ? AND ?");
//        assertThat(result.getParameters()).containsExactly(5, 15);
//    }
//
//    @Test
//    void jsonToSql_withJoin_complexNested() throws Exception {
//        EntitySchema schema = createParentChildSchema();
//
//        String json = """
//                {
//                    "select":[["id"],["name"],["children","id"],["children","name"]],
//                    "where":{"logician":"AND",
//                        "criteria":[
//                            {"field":["name"],"operator":"LIKE","value":"%test%"},
//                            {"logician":"OR","criteria":[
//                                {"field":["children","value"],"operator":"GT","value":10},
//                                {"field":["children","value"],"operator":"LT","value":5}
//                                ]
//                            }
//                        ]
//                    }
//                }
//                """;
//        Query query = objectMapper.readValue(json, Query.class);
//        CompiledQuery result = compiler.compile(query, QueryMeta.builder().build());
//
//        assertThat(result.getSql()).contains("LEFT JOIN");
//        assertThat(result.getSql()).contains("\"parent\".\"id\"");
//        assertThat(result.getSql()).contains("\"parent\".\"name\"");
//        assertThat(result.getSql()).contains("\"children\".\"id\"");
//        assertThat(result.getSql()).contains("\"children\".\"name\"");
//        assertThat(result.getSql()).contains("AND");
//        assertThat(result.getSql()).contains("OR");
//        assertThat(result.getParameters()).containsExactly("%test%", 10, 5);
//    }
//
//    //SELECT "parent"."id", "parent"."name", "children"."id", "children"."name" FROM "parent" "parent" LEFT JOIN "child" "children" ON "parent"."id" = "children"."parent_id" WHERE ("parent"."name" LIKE ?) AND (("children"."value" > ?) OR ("children"."value" < ?))
//
//    private EntitySchema createParentChildSchema() {
//        Table child = Table.builder()
//                .name("child")
//                .alias("children")
//                .columns(List.of(
//                        Column.builder().name("id").alias("id").build(),
//                        Column.builder().name("parent_id").alias("parent_id").build(),
//                        Column.builder().name("name").alias("name").build(),
//                        Column.builder().name("value").alias("value").build()))
//                .relations(List.of())
//                .build();
//        Table parent = Table.builder()
//                .name("parent")
//                .alias("parent")
//                .columns(List.of(
//                        Column.builder().name("id").alias("id").build(),
//                        Column.builder().name("name").alias("name").build(),
//                        Column.builder().name("description").alias("description").build()))
//                .relations(List.of(Relation.builder()
//                        .targetTableName("child")
//                        .thisColumnName("id")
//                        .targetColumnName("parent_id")
//                        .build()))
//                .build();
//        return EntitySchema.of(Map.of("parent", parent, "children", child));
//    }
}
