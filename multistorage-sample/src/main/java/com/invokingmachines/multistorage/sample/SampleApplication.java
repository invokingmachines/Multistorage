package com.invokingmachines.multistorage.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = {
        "com.invokingmachines.multistorage.entity",
        "com.invokingmachines.multistorage.sample"
})
@EnableJpaRepositories(basePackages = "com.invokingmachines.multistorage.sample.tenant")
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }
}
