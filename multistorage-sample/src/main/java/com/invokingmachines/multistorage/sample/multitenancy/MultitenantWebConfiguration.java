package com.invokingmachines.multistorage.sample.multitenancy;

import com.invokingmachines.multistorage.config.MultistorageWebProperties;
import com.invokingmachines.multistorage.liquibase.LiquibaseSchemaTarget;
import com.invokingmachines.multistorage.liquibase.LiquibaseTargetSchemaProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Optional;

@Configuration
public class MultitenantWebConfiguration {

    @Bean
    LiquibaseTargetSchemaProvider liquibaseTargetSchemaProvider(TenantSchemaRegistry tenantSchemaRegistry) {
        return () -> Optional.ofNullable(TenantContext.getTenantCode())
                .filter(code -> !code.isBlank())
                .flatMap(tenantSchemaRegistry::getSchemaForCode)
                .map(schema -> new LiquibaseSchemaTarget(schema, schema));
    }

    @Bean
    FilterRegistrationBean<TenantPathFilter> tenantPathFilterRegistration(MultistorageWebProperties webProperties,
                                                                          TenantSchemaRegistry tenantSchemaRegistry) {
        FilterRegistrationBean<TenantPathFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new TenantPathFilter(webProperties, tenantSchemaRegistry));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
