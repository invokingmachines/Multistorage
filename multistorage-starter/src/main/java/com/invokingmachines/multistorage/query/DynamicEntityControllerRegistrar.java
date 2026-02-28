package com.invokingmachines.multistorage.query;

import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.meta.MetaProvider;
import com.invokingmachines.multistorage.meta.dto.MetaRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("liquibase")
public class DynamicEntityControllerRegistrar implements ApplicationRunner, Ordered {

    private final MetaProvider metaProvider;
    private final RequestMappingHandlerMapping handlerMapping;
    private final EntitySearchHandler entitySearchHandler;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, ?> tables = metaProvider.getMeta(MetaRequest.builder().build()).getTables();
        Method searchMethod = getSearchMethod();

        tables.keySet().forEach(entityAlias -> {
            RequestMappingInfo mapping = RequestMappingInfo.paths("/multistorage/api/" + entityAlias + "/search")
                    .methods(RequestMethod.POST)
                    .consumes(MediaType.APPLICATION_JSON_VALUE)
                    .produces(MediaType.APPLICATION_JSON_VALUE)
                    .build();
            handlerMapping.registerMapping(mapping, entitySearchHandler, searchMethod);
            log.debug("Registered search endpoint for entity: {}", entityAlias);
        });

        log.info("Registered {} entity search endpoints", tables.size());
    }

    private Method getSearchMethod() {
        try {
            return EntitySearchHandler.class.getMethod("search", HttpServletRequest.class, Query.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("EntitySearchHandler.search method not found", e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
