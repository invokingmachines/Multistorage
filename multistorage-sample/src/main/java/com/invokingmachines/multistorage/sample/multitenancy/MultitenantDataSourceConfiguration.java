package com.invokingmachines.multistorage.sample.multitenancy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;

import javax.sql.DataSource;

@Configuration
public class MultitenantDataSourceConfiguration {

    @Bean
    static BeanPostProcessor multitenantDataSourceBeanPostProcessor(ObjectProvider<TenantSchemaRegistry> tenantSchemaRegistryProvider) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
                if (!"dataSource".equals(beanName) || !(bean instanceof DataSource dataSource)) {
                    return bean;
                }
                if (dataSource instanceof MultitenantDataSource) {
                    return bean;
                }
                return new MultitenantDataSource(dataSource, tenantSchemaRegistryProvider::getObject);
            }
        };
    }
}
