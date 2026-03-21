package com.invokingmachines.multistorage.sample.multitenancy;

import com.invokingmachines.multistorage.config.MultistorageWebProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class MultitenantWebConfiguration {

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
