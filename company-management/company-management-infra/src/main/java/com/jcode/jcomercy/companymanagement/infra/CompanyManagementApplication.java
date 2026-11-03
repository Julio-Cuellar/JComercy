package com.jcode.jcomercy.companymanagement.infra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan(basePackages = "com.jcode.jcomercy.companymanagement.infra.persistence.entity")
@EnableJpaRepositories(basePackages = "com.jcode.jcomercy.companymanagement.infra.persistence.repository")
public class CompanyManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(CompanyManagementApplication.class, args);
    }
}
