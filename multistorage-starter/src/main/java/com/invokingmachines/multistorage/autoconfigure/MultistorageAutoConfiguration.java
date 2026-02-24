package com.invokingmachines.multistorage.autoconfigure;

import com.invokingmachines.multistorage.controller.QueryController;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@ConditionalOnClass(QueryController.class)
@ComponentScan(basePackages = "com.invokingmachines.multistorage")
public class MultistorageAutoConfiguration {
}
