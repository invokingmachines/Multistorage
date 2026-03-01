package com.invokingmachines.multistorage.query;

import com.invokingmachines.multistorage.meta.MetaProvider;
import com.invokingmachines.multistorage.meta.dto.MetaRequest;
import com.invokingmachines.multistorage.util.NamingUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EntitySearchOpenApiCustomizer implements GlobalOpenApiCustomizer {

    private static final Object EXAMPLE_QUERY = Map.of(
            "select", List.of(List.of("name")),
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
        Map<String, ?> tables = metaProvider.getMeta(MetaRequest.builder().build()).getTables();
        Schema<?> arrayMapSchema = new ArraySchema().items(new Schema<>().type("object"));

        tables.keySet().forEach(entityAlias -> {
            String tagName = tagName(entityAlias);
            openApi.addTagsItem(new Tag().name(tagName).description(entityAlias + " search API"));

            String path = "/multistorage/api/" + NamingUtils.toPathSegment(entityAlias) + "/search";
            Schema<?> querySchema = new Schema<>().type("object").description("Query: select, where");
            MediaType requestMediaType = new MediaType()
                    .schema(querySchema)
                    .example(EXAMPLE_QUERY);
            Operation post = new Operation()
                    .operationId("search_" + entityAlias)
                    .summary("Search " + entityAlias)
                    .description("Execute search query for entity " + entityAlias + ". Body: Query (select, where).")
                    .addTagsItem(tagName)
                    .requestBody(new RequestBody()
                            .required(true)
                            .content(new Content().addMediaType("application/json", requestMediaType)))
                    .responses(new ApiResponses()
                            .addApiResponse("200", new ApiResponse()
                                    .description("Search results")
                                    .content(new Content().addMediaType("application/json", new MediaType().schema(arrayMapSchema)))));
            openApi.path(path, new PathItem().post(post));
        });
    }

    private static String tagName(String entityAlias) {
        String capitalized = entityAlias.length() > 0
                ? entityAlias.substring(0, 1).toUpperCase() + entityAlias.substring(1)
                : entityAlias;
        return capitalized + " Controller";
    }
}
