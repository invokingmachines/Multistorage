package com.invokingmachines.multistorage.autoconfigure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
}
