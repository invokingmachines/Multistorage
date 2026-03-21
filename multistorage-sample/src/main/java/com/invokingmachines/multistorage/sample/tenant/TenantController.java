package com.invokingmachines.multistorage.sample.tenant;

import com.invokingmachines.multistorage.sample.multitenancy.TenantSchemaRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantSchemaRegistry tenantSchemaRegistry;

    @GetMapping
    public List<TenantResponse> list() {
        return tenantSchemaRegistry.listOrdered().stream()
                .map(t -> new TenantResponse(t.getId(), t.getCode()))
                .toList();
    }
}
