package com.invokingmachines.multistorage.dto.query;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Query {

    private List<List<String>> select;
    private Criteria where = new Criteria(Logician.AND, Collections.emptyList());
}
