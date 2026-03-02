package com.invokingmachines.multistorage.autoconfigure;

import com.invokingmachines.multistorage.config.MultistorageScanProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ComponentScan(basePackages = {
        "com.invokingmachines.multistorage.meta",
        "com.invokingmachines.multistorage.pipeline",
        "com.invokingmachines.multistorage.query",
        "com.invokingmachines.multistorage.config"
})
@EntityScan(basePackages = "com.invokingmachines.multistorage.entity")
@EnableJpaRepositories(basePackages = "com.invokingmachines.multistorage.repository")
@EnableConfigurationProperties(MultistorageScanProperties.class)
public class MultistorageAutoConfiguration {
}
