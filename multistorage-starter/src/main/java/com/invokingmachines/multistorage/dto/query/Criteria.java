package com.invokingmachines.multistorage.dto.query;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Criteria extends Node{

    private Logician logician;
    private List<? extends Node> criteria;
}
