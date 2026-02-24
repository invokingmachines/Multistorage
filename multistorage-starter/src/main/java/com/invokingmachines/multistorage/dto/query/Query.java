package com.invokingmachines.multistorage.dto.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Query {

    private String target;
    private List<List<String>> select;
    private Criteria where;
}
