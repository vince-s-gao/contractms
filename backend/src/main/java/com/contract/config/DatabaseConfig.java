package com.contract.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * 数据库配置类
 * 配置数据源和连接池
 */
@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    /**
     * 配置HikariCP数据源
     */
    @Bean
    public DataSource dataSource() {
        validatePasswordBeforeConnect();
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setMaximumPoolSize(maxPoolSize);
        dataSource.setMinimumIdle(minIdle);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setIdleTimeout(idleTimeout);
        dataSource.setMaxLifetime(maxLifetime);
        dataSource.setPoolName("ContractManagementPool");
        
        // 连接测试配置
        dataSource.setConnectionTestQuery("SELECT 1");
        dataSource.setLeakDetectionThreshold(60000);
        
        return dataSource;
    }

    private void validatePasswordBeforeConnect() {
        String effectivePassword = trimToNull(password);
        String dbPassword = trimToNull(System.getenv("DB_PASSWORD"));
        String springDatasourcePassword = trimToNull(System.getenv("SPRING_DATASOURCE_PASSWORD"));

        if (isUnresolvedPlaceholder(effectivePassword)
                || isUnresolvedPlaceholder(dbPassword)
                || isUnresolvedPlaceholder(springDatasourcePassword)) {
            throw new IllegalStateException("数据库密码配置未解析，请检查 DB_PASSWORD/SPRING_DATASOURCE_PASSWORD");
        }
        if (effectivePassword == null) {
            throw new IllegalStateException("数据库密码未配置，请设置 DB_PASSWORD 或 SPRING_DATASOURCE_PASSWORD");
        }
        if (dbPassword == null && springDatasourcePassword == null) {
            throw new IllegalStateException("数据库密码环境变量未配置，请设置 DB_PASSWORD 或 SPRING_DATASOURCE_PASSWORD");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isUnresolvedPlaceholder(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.startsWith("${") && trimmed.endsWith("}");
    }

    /**
     * 配置JdbcTemplate
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
