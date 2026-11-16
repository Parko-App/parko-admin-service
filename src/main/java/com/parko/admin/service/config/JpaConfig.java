package com.parko.admin.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.parko.admin.service.repository")
public class JpaConfig {
}
