package com.invokingmachines.multistorage.meta;

import com.invokingmachines.multistorage.dto.meta.QueryMeta;
import com.invokingmachines.multistorage.meta.dto.MetaRequest;

public interface MetaProvider {

    QueryMeta getMeta(MetaRequest request);
}
