package com.invokingmachines.multistorage.data.dto;

import lombok.Builder;
import lombok.Data;
import org.jooq.ResultQuery;


@Data
@Builder
public class CompiledQuery {

    private ResultQuery<?> query;
}
