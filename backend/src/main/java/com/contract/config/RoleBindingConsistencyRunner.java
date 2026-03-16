package com.contract.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RoleBindingConsistencyRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleBindingConsistencyRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public RoleBindingConsistencyRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tableExists("roles") || !tableExists("users")) {
            return;
        }

        Long canonicalUserRoleId = querySingleRoleIdByCode("USER");
        List<Long> legacyRoleIds = queryRoleIdsByCode("ROLE_USER");

        if (canonicalUserRoleId == null && !legacyRoleIds.isEmpty()) {
            Long promotedRoleId = legacyRoleIds.get(0);
            jdbcTemplate.update("UPDATE roles SET role_code = 'USER', updated_at = NOW() WHERE id = ?", promotedRoleId);
            canonicalUserRoleId = promotedRoleId;
            legacyRoleIds = legacyRoleIds.subList(1, legacyRoleIds.size());
            log.info("Promoted legacy ROLE_USER role to USER, roleId={}", promotedRoleId);
        }

        if (canonicalUserRoleId == null) {
            return;
        }

        String mergedPermissions = normalizePermissions(joinPermissions(
                queryRolePermissions(canonicalUserRoleId),
                queryRolePermissions(legacyRoleIds)
        ));
        jdbcTemplate.update("UPDATE roles SET permissions = ?, updated_at = NOW() WHERE id = ?",
                nullable(mergedPermissions), canonicalUserRoleId);

        for (Long legacyRoleId : legacyRoleIds) {
            int moved = jdbcTemplate.update(
                    "UPDATE users SET role_id = ?, role = 'USER' WHERE role_id = ?",
                    canonicalUserRoleId, legacyRoleId
            );
            jdbcTemplate.update("DELETE FROM roles WHERE id = ? AND role_code = 'ROLE_USER'", legacyRoleId);
            if (moved > 0) {
                log.info("Rebound users from legacy role to USER: fromRoleId={}, toRoleId={}, moved={}",
                        legacyRoleId, canonicalUserRoleId, moved);
            }
        }

        jdbcTemplate.update(
                "UPDATE users SET role = 'USER' WHERE role_id = ? AND (role IS NULL OR UPPER(role) <> 'USER')",
                canonicalUserRoleId
        );
        jdbcTemplate.update(
                "UPDATE roles SET role_code = 'USER', updated_at = NOW() WHERE id = ? AND UPPER(role_code) <> 'USER'",
                canonicalUserRoleId
        );
    }

    private boolean tableExists(String tableName) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Long.class, tableName);
        return count != null && count > 0;
    }

    private Long querySingleRoleIdByCode(String roleCode) {
        List<Long> roleIds = jdbcTemplate.queryForList(
                "SELECT id FROM roles WHERE UPPER(role_code) = UPPER(?) ORDER BY id ASC LIMIT 1",
                Long.class,
                roleCode
        );
        return roleIds.isEmpty() ? null : roleIds.get(0);
    }

    private List<Long> queryRoleIdsByCode(String roleCode) {
        List<Long> roleIds = jdbcTemplate.queryForList(
                "SELECT id FROM roles WHERE UPPER(role_code) = UPPER(?) ORDER BY id ASC",
                Long.class,
                roleCode
        );
        return roleIds == null ? new ArrayList<>() : roleIds;
    }

    private String queryRolePermissions(Long roleId) {
        List<String> values = jdbcTemplate.queryForList(
                "SELECT COALESCE(permissions, '') FROM roles WHERE id = ? LIMIT 1",
                String.class,
                roleId
        );
        return values.isEmpty() ? "" : values.get(0);
    }

    private List<String> queryRolePermissions(List<Long> roleIds) {
        List<String> values = new ArrayList<>();
        for (Long roleId : roleIds) {
            values.add(queryRolePermissions(roleId));
        }
        return values;
    }

    private static List<String> joinPermissions(String basePermissions, List<String> permissionsList) {
        List<String> all = new ArrayList<>();
        all.add(basePermissions);
        all.addAll(permissionsList);
        return all;
    }

    private static String normalizePermissions(List<String> rawPermissions) {
        Set<String> merged = new LinkedHashSet<>();
        for (String raw : rawPermissions) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String item : raw.split(",")) {
                String code = item == null ? "" : item.trim();
                if (!code.isEmpty()) {
                    merged.add(code);
                }
            }
        }
        return String.join(",", merged);
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
