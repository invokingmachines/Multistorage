package com.invokingmachines.multistorage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "multistorage.web")
public class MultistorageWebProperties {

    private String apiTenantPrefix = "/api";

    private String apiPrefix = "/api/{tenantId}";
}
