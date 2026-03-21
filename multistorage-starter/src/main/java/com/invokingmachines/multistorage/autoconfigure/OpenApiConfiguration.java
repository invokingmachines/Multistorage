package com.invokingmachines.multistorage.autoconfigure;

import com.invokingmachines.multistorage.config.MultistorageWebProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class OpenApiConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI multistorageOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Multistorage API")
                        .description("Multistorage Spring Boot Starter API Documentation")
                        .version("0.0.1-SNAPSHOT"));
    }

    @Bean
    public GroupedOpenApi adminApi(MultistorageWebProperties webProperties) {
        String p = normalizePrefix(webProperties.getApiTenantPrefix());
        return GroupedOpenApi.builder()
                .group("admin")
                .displayName("Admin: Meta configuration")
                .pathsToMatch(p + "/*/admin/**")
                .pathsToExclude(p + "/**/search/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userApi(MultistorageWebProperties webProperties) {
        String p = normalizePrefix(webProperties.getApiTenantPrefix());
        return GroupedOpenApi.builder()
                .group("user")
                .displayName("User: Discovery & Search")
                .pathsToMatch(p + "/**")
                .pathsToExclude(p + "/*/admin/**")
                .build();
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }
}
