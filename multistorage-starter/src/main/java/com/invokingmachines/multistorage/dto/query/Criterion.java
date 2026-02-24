package com.invokingmachines.multistorage.dto.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Criterion extends Node {

    private Quantifier quantifier;
    private Operator operator;
    private Object value;
    private List<String> field;
}
