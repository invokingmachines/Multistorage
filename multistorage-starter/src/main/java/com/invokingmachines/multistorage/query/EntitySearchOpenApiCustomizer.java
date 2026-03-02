package com.invokingmachines.multistorage.query;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.meta.MetaProvider;
import com.invokingmachines.multistorage.meta.dto.MetaRequest;
import com.invokingmachines.multistorage.util.NamingUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EntitySearchOpenApiCustomizer implements GlobalOpenApiCustomizer {

    private static final Object EXAMPLE_QUERY = Map.of(
            "select", List.of(List.of("id"), List.of("name")),
            "where", Map.of(
                    "logician", "AND",
                    "criteria", List.of(Map.of(
                            "field", List.of("name"),
                            "operator", "EQ",
                            "value", "example"
                    ))
            )
    );

    private final MetaProvider metaProvider;

    @Override
    public void customise(OpenAPI openApi) {
        QueryMeta meta = metaProvider.getMeta(MetaRequest.builder().build());
        Map<String, TableMeta> tables = meta.getTables();
        Map<String, TableMeta> uniqueTables = new LinkedHashMap<>();
        tables.forEach((key, tm) -> uniqueTables.putIfAbsent(tm.getName(), tm));

        uniqueTables.forEach((tableName, tableMeta) -> {
            String pathSegment = NamingUtils.toPathSegment(tableName);
            String tagName = tagName(tableName);

            openApi.addTagsItem(new Tag().name(tagName).description(tableName + " — search, upsert, delete"));

            addSearchPath(openApi, tableName, pathSegment, tagName, tableMeta);
            addUpsertPath(openApi, tableName, pathSegment, tagName, tableMeta);
            addDeletePath(openApi, tableName, pathSegment, tagName);
        });
    }

    private void addSearchPath(OpenAPI openApi, String tableName, String pathSegment, String tagName, TableMeta tableMeta) {
        Schema<?> querySchema = new Schema<>().type("object").description("Query: select, where");
        MediaType searchRequestMedia = new MediaType().schema(querySchema).example(EXAMPLE_QUERY);

        Object searchResponseExample = List.of(buildEntityExample(tableMeta, meta -> null, 1L));
        Schema<?> arraySchema = new Schema<>().type("array").items(new Schema<>().type("object"));
        MediaType searchResponseMedia = new MediaType().schema(arraySchema).example(searchResponseExample);

        Operation op = new Operation()
                .operationId("search_" + tableName)
                .summary("Search " + tableName)
                .description("Execute search query. Body: select (field paths), where (criteria).")
                .addTagsItem(tagName)
                .requestBody(new RequestBody().required(true)
                        .content(new Content().addMediaType("application/json", searchRequestMedia)))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("Search results")
                                .content(new Content().addMediaType("application/json", searchResponseMedia))));
        openApi.path("/multistorage/api/" + pathSegment + "/search", new PathItem().post(op));
    }

    private void addUpsertPath(OpenAPI openApi, String tableName, String pathSegment, String tagName,
                              TableMeta tableMeta) {
        Object upsertRequestExample = buildEntityExample(tableMeta, rel -> Map.of("id", 1), null);
        Schema<?> entitySchema = new Schema<>().type("object").description("Entity to create or update (upsert)");
        MediaType upsertRequestMedia = new MediaType().schema(entitySchema).example(upsertRequestExample);

        Object upsertResponseExample = buildEntityExample(tableMeta, rel -> Map.of("id", 1), 1L);
        MediaType upsertResponseMedia = new MediaType()
                .schema(new Schema<>().type("object"))
                .example(upsertResponseExample);

        Operation op = new Operation()
                .operationId("upsert_" + tableName)
                .summary("Upsert " + tableName)
                .description("Create or update entity. If id present — update, else insert. Relations with cascade can be nested.")
                .addTagsItem(tagName)
                .requestBody(new RequestBody().required(true)
                        .content(new Content().addMediaType("application/json", upsertRequestMedia)))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("Saved entity with id")
                                .content(new Content().addMediaType("application/json", upsertResponseMedia))));
        openApi.path("/multistorage/api/" + pathSegment, new PathItem().post(op));
    }

    private void addDeletePath(OpenAPI openApi, String tableName, String pathSegment, String tagName) {
        Operation op = new Operation()
                .operationId("delete_" + tableName)
                .summary("Delete " + tableName)
                .description("Delete entity by id.")
                .addTagsItem(tagName)
                .addParametersItem(new Parameter()
                        .name("id")
                        .in("path")
                        .required(true)
                        .description("Entity id")
                        .schema(new Schema<>().type("string").example("1")))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("Deleted")
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new Schema<>().type("object"))
                                                .example(Map.of("deleted", true))))));
        openApi.path("/multistorage/api/" + pathSegment + "/{id}", new PathItem().delete(op));
    }

    private Map<String, Object> buildEntityExample(TableMeta table, java.util.function.Function<String, Object> relationExample, Long id) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (id != null) map.put("id", id);
        if (table.getColumns() != null) {
            table.getColumns().forEach((colName, col) -> {
                String key = col.getAlias() != null && !col.getAlias().isBlank() ? col.getAlias() : colName;
                if ("id".equals(key) && id != null) return;
                map.put(key, exampleValue(col.getDataType()));
            });
        }
        if (table.getRelations() != null && relationExample != null) {
            table.getRelations().forEach((relAlias, rel) -> {
                Object relEx = relationExample.apply(relAlias);
                if (relEx != null) {
                    map.put(relAlias, rel.isOneToMany() ? List.of(relEx) : relEx);
                }
            });
        }
        return map;
    }

    private static Object exampleValue(String dataType) {
        if (dataType == null) return "example";
        return switch (dataType.toLowerCase()) {
            case "int8", "int4", "int2", "bigint", "integer", "smallint" -> 1;
            case "float8", "float4", "numeric", "decimal" -> 1.0;
            case "bool", "boolean" -> true;
            case "timestamp", "timestamptz", "date" -> "2024-01-15T10:00:00";
            default -> "example";
        };
    }

    private static String tagName(String entityKey) {
        String capitalized = entityKey.length() > 0
                ? entityKey.substring(0, 1).toUpperCase() + entityKey.substring(1)
                : entityKey;
        return capitalized + " Controller";
    }
}
