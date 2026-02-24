package com.invokingmachines.multistorage.compiler;

import lombok.Builder;
import lombok.Data;

import java.util.List;


@Data
@Builder
public class CompiledQuery {

    private String sql;
    private List<Object> parameters;
}
