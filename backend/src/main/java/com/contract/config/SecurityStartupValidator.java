package com.contract.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
public class SecurityStartupValidator {

    private static final Set<String> PROD_PROFILES = Set.of("prod", "production");

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${app.security.jwt-min-secret-length:32}")
    private int jwtMinSecretLength;

    @Value("${app.security.prod-db-min-password-length:12}")
    private int prodDbMinPasswordLength;

    private final Environment environment;

    public SecurityStartupValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        validateJwtSecret();
        validateDbPassword();
    }

    private void validateJwtSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置，服务启动终止");
        }
        if (jwtSecret.length() < jwtMinSecretLength) {
            throw new IllegalStateException("JWT_SECRET 长度不足，至少需要 " + jwtMinSecretLength + " 位");
        }
    }

    private void validateDbPassword() {
        String dbPasswordFromEnv = trimToNull(environment.getProperty("DB_PASSWORD"));
        String springDatasourcePassword = trimToNull(environment.getProperty("SPRING_DATASOURCE_PASSWORD"));
        String effectivePassword = trimToNull(dbPassword);

        if (isUnresolvedPlaceholder(effectivePassword)) {
            throw new IllegalStateException("spring.datasource.password 配置未解析，服务启动终止");
        }
        if (isUnresolvedPlaceholder(dbPasswordFromEnv) || isUnresolvedPlaceholder(springDatasourcePassword)) {
            throw new IllegalStateException("数据库密码环境变量配置未解析，服务启动终止");
        }
        if (effectivePassword == null) {
            throw new IllegalStateException("数据库密码未配置，请设置 DB_PASSWORD 或 SPRING_DATASOURCE_PASSWORD");
        }
        if (dbPasswordFromEnv == null && springDatasourcePassword == null) {
            throw new IllegalStateException("数据库密码环境变量未配置，请设置 DB_PASSWORD 或 SPRING_DATASOURCE_PASSWORD");
        }
        if (isProdProfile() && effectivePassword.length() < prodDbMinPasswordLength) {
            throw new IllegalStateException("生产环境 DB_PASSWORD 长度不足，至少需要 " + prodDbMinPasswordLength + " 位");
        }
    }

    private boolean isProdProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(PROD_PROFILES::contains);
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
}
