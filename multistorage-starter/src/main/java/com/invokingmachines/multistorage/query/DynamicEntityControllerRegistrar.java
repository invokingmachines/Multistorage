package com.invokingmachines.multistorage.query;

import com.invokingmachines.multistorage.dto.meta.TableMeta;
import com.invokingmachines.multistorage.dto.query.Query;
import com.invokingmachines.multistorage.meta.MetaProvider;
import com.invokingmachines.multistorage.util.NamingUtils;
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
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("liquibase")
public class DynamicEntityControllerRegistrar implements ApplicationRunner, Ordered {

    private final MetaProvider metaProvider;
    private final RequestMappingHandlerMapping handlerMapping;
    private final EntitySearchHandler entitySearchHandler;
    private final EntityUpsertHandler entityUpsertHandler;
    private final EntityDeleteHandler entityDeleteHandler;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, TableMeta> tables = metaProvider.getMeta(MetaRequest.builder().build()).getTables();
        Map<String, TableMeta> uniqueTables = new LinkedHashMap<>();
        tables.forEach((key, tm) -> uniqueTables.putIfAbsent(tm.getName(), tm));

        Method searchMethod = getSearchMethod();
        Method upsertMethod = getUpsertMethod();
        Method deleteMethod = getDeleteMethod();

        uniqueTables.keySet().forEach(entityAlias -> {
            String pathSegment = NamingUtils.toPathSegment(entityAlias);
            handlerMapping.registerMapping(
                    RequestMappingInfo.paths("/multistorage/api/" + pathSegment + "/search")
                            .methods(RequestMethod.POST)
                            .consumes(MediaType.APPLICATION_JSON_VALUE)
                            .produces(MediaType.APPLICATION_JSON_VALUE)
                            .build(),
                    entitySearchHandler, searchMethod);
            handlerMapping.registerMapping(
                    RequestMappingInfo.paths("/multistorage/api/" + pathSegment)
                            .methods(RequestMethod.POST)
                            .consumes(MediaType.APPLICATION_JSON_VALUE)
                            .produces(MediaType.APPLICATION_JSON_VALUE)
                            .build(),
                    entityUpsertHandler, upsertMethod);
            handlerMapping.registerMapping(
                    RequestMappingInfo.paths("/multistorage/api/" + pathSegment + "/{id}")
                            .methods(RequestMethod.DELETE)
                            .produces(MediaType.APPLICATION_JSON_VALUE)
                            .build(),
                    entityDeleteHandler, deleteMethod);
            log.debug("Registered endpoints for entity: {}", entityAlias);
        });

        log.info("Registered {} entity endpoints (search, upsert, delete)", uniqueTables.size());
    }

    private Method getSearchMethod() {
        try {
            return EntitySearchHandler.class.getMethod("search", HttpServletRequest.class, Query.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("EntitySearchHandler.search method not found", e);
        }
    }

    private Method getUpsertMethod() {
        try {
            return EntityUpsertHandler.class.getMethod("upsert", HttpServletRequest.class, Object.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("EntityUpsertHandler.upsert method not found", e);
        }
    }

    private Method getDeleteMethod() {
        try {
            return EntityDeleteHandler.class.getMethod("delete", HttpServletRequest.class, String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("EntityDeleteHandler.delete method not found", e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
