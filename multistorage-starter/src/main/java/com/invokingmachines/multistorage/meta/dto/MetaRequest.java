package com.invokingmachines.multistorage.meta.dto;

import lombok.Builder;
import lombok.Data;

import java.security.Principal;

@Data
@Builder
public class MetaRequest {

    private Principal principal;
    private Object context;
}
