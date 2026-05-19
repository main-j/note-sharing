package com.project.login.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Value("${spring.flyway.schemas}")
    private String schemas;

    @Value("${spring.flyway.locations}")
    private String locations;

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemas)
                .locations(locations)
                .baselineOnMigrate(true)  // ✅ 对已有非空 schema 自动创建基线
                .baselineVersion("1")     // 仅在对“已有表但无 flyway 历史”的场景打基线时使用
                .load();
        // 不要在此调用 flyway.baseline()：会把库标成已在 v1 且不执行 V1 脚本，导致后续迁移缺表失败。
        return flyway;
    }
}

