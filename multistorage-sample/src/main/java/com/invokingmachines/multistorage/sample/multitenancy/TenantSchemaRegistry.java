package com.invokingmachines.multistorage.sample.multitenancy;

import com.invokingmachines.multistorage.sample.tenant.TenantEntity;
import com.invokingmachines.multistorage.sample.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class TenantSchemaRegistry {

    private final TenantRepository tenantRepository;
    private final ConcurrentMap<String, String> codeToSchema = new ConcurrentHashMap<>();

    public static String schemaNameForTenantId(long id) {
        return "_" + id + "_sample";
    }

    public void refresh() {
        codeToSchema.clear();
        tenantRepository.findAll().forEach(t -> codeToSchema.put(t.getCode(), schemaNameForTenantId(t.getId())));
    }

    public boolean hasTenant(String code) {
        return codeToSchema.containsKey(code);
    }

    public Optional<String> getSchemaForCode(String code) {
        return Optional.ofNullable(codeToSchema.get(code));
    }

    public List<TenantEntity> listOrdered() {
        return tenantRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }
}
