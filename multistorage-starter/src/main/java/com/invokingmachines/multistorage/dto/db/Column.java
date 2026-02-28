package com.invokingmachines.multistorage.dto.db;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Column {
    private String name;
    private String type;
    private Integer size;
    private Integer decimalDigits;
    private Boolean nullable;
    private Boolean primaryKey;
    private String defaultValue;
    private String remarks;
}
