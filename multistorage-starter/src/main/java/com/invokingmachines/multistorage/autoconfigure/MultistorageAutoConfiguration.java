package com.invokingmachines.multistorage.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invokingmachines.multistorage.config.MultistorageScanProperties;
import com.invokingmachines.multistorage.config.MultistorageWebProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ComponentScan(basePackages = {
        "com.invokingmachines.multistorage.meta",
        "com.invokingmachines.multistorage.pipeline",
        "com.invokingmachines.multistorage.data",
        "com.invokingmachines.multistorage.openapi",
        "com.invokingmachines.multistorage.config"
})
@EntityScan(basePackages = "com.invokingmachines.multistorage.entity")
@EnableJpaRepositories(basePackages = "com.invokingmachines.multistorage.repository")
@EnableConfigurationProperties({MultistorageScanProperties.class, MultistorageWebProperties.class})
public class MultistorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
