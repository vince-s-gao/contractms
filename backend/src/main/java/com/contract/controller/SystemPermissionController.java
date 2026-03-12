package com.contract.controller;

import com.contract.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/system")
@PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN')")
public class SystemPermissionController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OperationLogService operationLogService;

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(@RequestParam(required = false) String keyword, Authentication authentication) {
        StringBuilder sql = new StringBuilder("""
                SELECT u.id,
                       u.username,
                       u.real_name AS realName,
                       u.email,
                       u.role_id AS roleId,
                       COALESCE(r.role_name, '') AS roleName,
                       COALESCE(r.role_code, '') AS roleCode,
                       CASE
                           WHEN u.enabled = b'1' OR u.enabled = 1 THEN 1
                           ELSE 0
                       END AS enabled
                FROM users u
                LEFT JOIN roles r ON u.role_id = r.id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (!isBlank(keyword)) {
            sql.append(" AND (u.username LIKE ? OR u.real_name LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        sql.append(" ORDER BY u.id DESC");
        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        for (Map<String, Object> record : records) {
            String username = asString(record.get("username"));
            String roleCode = asString(record.get("roleCode"));
            String roleName = asString(record.get("roleName"));
            String normalizedRoleName = normalizedBuiltinRoleName(roleCode);
            if (!isBlank(normalizedRoleName)) {
                record.put("roleName", normalizedRoleName);
            } else if (looksMojibake(roleName)) {
                record.put("roleName", roleCode);
            }

            String realName = asString(record.get("realName"));
            if (looksMojibake(realName)) {
                if ("admin".equalsIgnoreCase(username)) {
                    record.put("realName", "系统管理员");
                } else {
                    record.put("realName", username);
                }
            }
        }
        operationLogService.log(authentication, "QUERY", "PERMISSION", "查询用户角色列表", "SUCCESS", null);
        return ResponseEntity.ok(Map.of("records", records, "total", records.size()));
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        Long roleId = asLong(body.get("roleId"));
        if (roleId == null) {
            operationLogService.log(authentication, "UPDATE", "PERMISSION", "更新用户角色失败：roleId为空", "FAILED", "roleId不能为空");
            return ResponseEntity.badRequest().body(Map.of("message", "roleId不能为空"));
        }
        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, id);
        if (userCount == null || userCount == 0) {
            operationLogService.log(authentication, "UPDATE", "PERMISSION", "更新用户角色失败：用户不存在 id=" + id, "FAILED", "用户不存在");
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> roles = jdbcTemplate.queryForList(
                "SELECT id, role_code FROM roles WHERE id = ? LIMIT 1", roleId);
        if (roles.isEmpty()) {
            operationLogService.log(authentication, "UPDATE", "PERMISSION", "更新用户角色失败：角色不存在 roleId=" + roleId, "FAILED", "角色不存在");
            return ResponseEntity.badRequest().body(Map.of("message", "角色不存在"));
        }
        String roleCode = asString(roles.get(0).get("role_code"));
        String roleEnum = mapRoleCodeToUserEnum(roleCode);
        jdbcTemplate.update("UPDATE users SET role_id = ?, role = ? WHERE id = ?", roleId, roleEnum, id);
        operationLogService.log(authentication, "UPDATE", "PERMISSION", "更新用户角色成功，userId=" + id + "，roleId=" + roleId, "SUCCESS", null);
        return ResponseEntity.ok(Map.of("message", "用户角色更新成功"));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, Authentication authentication) {
        if (!isAdmin(authentication)) {
            operationLogService.log(authentication, "DELETE", "PERMISSION", "删除用户失败：非管理员 userId=" + id, "FAILED", "仅管理员可删除用户");
            return ResponseEntity.status(403).body(Map.of("message", "仅管理员可删除用户"));
        }
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, username FROM users WHERE id = ? LIMIT 1", id);
        if (users.isEmpty()) {
            operationLogService.log(authentication, "DELETE", "PERMISSION", "删除用户失败：用户不存在 id=" + id, "FAILED", "用户不存在");
            return ResponseEntity.notFound().build();
        }
        String targetUsername = asString(users.get(0).get("username"));
        if ("admin".equalsIgnoreCase(targetUsername)) {
            operationLogService.log(authentication, "DELETE", "PERMISSION", "删除用户失败：默认管理员不可删除", "FAILED", "默认管理员账号不可删除");
            return ResponseEntity.badRequest().body(Map.of("message", "默认管理员账号不可删除"));
        }
        String currentUsername = authentication == null ? null : authentication.getName();
        if (!isBlank(currentUsername) && currentUsername.equalsIgnoreCase(targetUsername)) {
            operationLogService.log(authentication, "DELETE", "PERMISSION", "删除用户失败：不能删除当前登录用户 " + targetUsername, "FAILED", "不能删除当前登录用户");
            return ResponseEntity.badRequest().body(Map.of("message", "不能删除当前登录用户"));
        }
        try {
            int affected = jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
            if (affected == 0) {
                operationLogService.log(authentication, "DELETE", "PERMISSION", "删除用户失败：用户不存在 id=" + id, "FAILED", "用户不存在");
                return ResponseEntity.notFound().build();
            }
            operationLogService.log(authentication, "DELETE", "PERMISSION", "删除用户成功，username=" + targetUsername + "，id=" + id, "SUCCESS", null);
            return ResponseEntity.ok(Map.of("message", "用户删除成功"));
        } catch (Exception e) {
            operationLogService.log(authentication, "DELETE", "PERMISSION", "删除用户失败：存在关联数据 username=" + targetUsername, "FAILED", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", "用户存在关联数据，无法删除"));
        }
    }

    @GetMapping("/roles")
    public ResponseEntity<?> getRoles(Authentication authentication) {
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT id,
                       role_name AS roleName,
                       role_code AS roleCode,
                       COALESCE(description, '') AS description,
                       COALESCE(permissions, '') AS permissions,
                       status
                FROM roles
                ORDER BY id ASC
                """);
        for (Map<String, Object> record : records) {
            String roleCode = asString(record.get("roleCode"));
            String normalizedName = normalizedBuiltinRoleName(roleCode);
            String normalizedDesc = normalizedBuiltinRoleDesc(roleCode);
            if (!isBlank(normalizedName)) {
                record.put("roleName", normalizedName);
            } else if (looksMojibake(asString(record.get("roleName")))) {
                record.put("roleName", roleCode);
            }
            if (!isBlank(normalizedDesc)) {
                record.put("description", normalizedDesc);
            }
            record.put("permissionCodes", splitPermissions(asString(record.get("permissions"))));
        }
        operationLogService.log(authentication, "QUERY", "PERMISSION", "查询角色列表", "SUCCESS", null);
        return ResponseEntity.ok(Map.of("records", records, "total", records.size()));
    }

    @PostMapping("/roles")
    public ResponseEntity<?> createRole(@RequestBody Map<String, Object> body, Authentication authentication) {
        String roleCode = normalizeRoleCode(asString(body.get("roleCode")));
        String roleName = asString(body.get("roleName"));
        String description = asString(body.get("description"));
        String permissions = String.join(",", extractPermissionCodes(body.get("permissionCodes")));
        if (isBlank(roleCode) || isBlank(roleName)) {
            operationLogService.log(authentication, "CREATE", "PERMISSION", "创建角色失败：编码或名称为空", "FAILED", "角色编码和角色名称不能为空");
            return ResponseEntity.badRequest().body(Map.of("message", "角色编码和角色名称不能为空"));
        }
        Long exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles WHERE role_code = ?", Long.class, roleCode);
        if (exists != null && exists > 0) {
            operationLogService.log(authentication, "CREATE", "PERMISSION", "创建角色失败：角色编码已存在 " + roleCode, "FAILED", "角色编码已存在");
            return ResponseEntity.badRequest().body(Map.of("message", "角色编码已存在"));
        }
        jdbcTemplate.update("""
                        INSERT INTO roles (role_name, role_code, description, permissions, status, created_at, updated_at)
                        VALUES (?, ?, ?, ?, 1, NOW(), NOW())
                        """,
                roleName.trim(), roleCode, nullable(description), nullable(permissions));
        operationLogService.log(authentication, "CREATE", "PERMISSION", "创建角色成功 " + roleCode, "SUCCESS", null);
        return ResponseEntity.ok(Map.of("message", "角色创建成功"));
    }

    @PutMapping("/roles/{id}")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody Map<String, Object> body, Authentication authentication) {
        String roleCode = normalizeRoleCode(asString(body.get("roleCode")));
        String roleName = asString(body.get("roleName"));
        String description = asString(body.get("description"));
        String permissions = String.join(",", extractPermissionCodes(body.get("permissionCodes")));
        if (isBlank(roleCode) || isBlank(roleName)) {
            operationLogService.log(authentication, "UPDATE", "PERMISSION", "更新角色失败：编码或名称为空 id=" + id, "FAILED", "角色编码和角色名称不能为空");
            return ResponseEntity.badRequest().body(Map.of("message", "角色编码和角色名称不能为空"));
        }
        Long exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles WHERE id = ?", Long.class, id);
        if (exists == null || exists == 0) {
            operationLogService.log(authentication, "UPDATE", "PERMISSION", "更新角色失败：角色不存在 id=" + id, "FAILED", "角色不存在");
            return ResponseEntity.notFound().build();
        }
        Long duplicate = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM roles WHERE role_code = ? AND id <> ?", Long.class, roleCode, id);
        if (duplicate != null && duplicate > 0) {
            operationLogService.log(authentication, "UPDATE", "PERMISSION", "更新角色失败：角色编码已存在 " + roleCode, "FAILED", "角色编码已存在");
            return ResponseEntity.badRequest().body(Map.of("message", "角色编码已存在"));
        }
        jdbcTemplate.update("""
                        UPDATE roles
                        SET role_name = ?,
                            role_code = ?,
                            description = ?,
                            permissions = ?,
                            updated_at = NOW()
                        WHERE id = ?
                        """,
                roleName.trim(), roleCode, nullable(description), nullable(permissions), id);
        operationLogService.log(authentication, "UPDATE", "PERMISSION", "更新角色成功 id=" + id + "，roleCode=" + roleCode, "SUCCESS", null);
        return ResponseEntity.ok(Map.of("message", "角色更新成功"));
    }

    @DeleteMapping("/roles/{id}")
    public ResponseEntity<?> deleteRole(@PathVariable Long id, Authentication authentication) {
        Long exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles WHERE id = ?", Long.class, id);
        if (exists == null || exists == 0) {
            operationLogService.log(authentication, "DELETE", "PERMISSION", "删除角色失败：角色不存在 id=" + id, "FAILED", "角色不存在");
            return ResponseEntity.notFound().build();
        }
        Long inUse = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE role_id = ?", Long.class, id);
        if (inUse != null && inUse > 0) {
            operationLogService.log(authentication, "DELETE", "PERMISSION", "删除角色失败：角色已被使用 id=" + id, "FAILED", "该角色已分配给用户，无法删除");
            return ResponseEntity.badRequest().body(Map.of("message", "该角色已分配给用户，无法删除"));
        }
        jdbcTemplate.update("DELETE FROM roles WHERE id = ?", id);
        operationLogService.log(authentication, "DELETE", "PERMISSION", "删除角色成功 id=" + id, "SUCCESS", null);
        return ResponseEntity.ok(Map.of("message", "角色删除成功"));
    }

    @GetMapping("/permissions")
    public ResponseEntity<?> getPermissions(Authentication authentication) {
        List<Map<String, Object>> records;
        if (tableExists("permissions")) {
            records = jdbcTemplate.queryForList("""
                    SELECT permission_code AS code,
                           permission_name AS name,
                           COALESCE(resource_type, '') AS module
                    FROM permissions
                    WHERE COALESCE(status, 'ACTIVE') IN ('ACTIVE', '1')
                    ORDER BY permission_code ASC
                    """);
        } else {
            records = new ArrayList<>();
        }
        if (records.isEmpty()) {
            records = defaultPermissions();
        }
        operationLogService.log(authentication, "QUERY", "PERMISSION", "查询权限列表", "SUCCESS", null);
        return ResponseEntity.ok(Map.of("records", records, "total", records.size()));
    }

    @GetMapping({"/operation-logs", "/operationLogs"})
    public ResponseEntity<?> getOperationLogs(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String username,
                                              @RequestParam(required = false) String module,
                                              @RequestParam(required = false) String operationType,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String startTime,
                                              @RequestParam(required = false) String endTime,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "10") int size,
                                              Authentication authentication) {
        Map<String, Object> result = operationLogService.queryLogs(
                keyword, username, module, operationType, status, startTime, endTime, page, size
        );
        return ResponseEntity.ok(result);
    }

    private static String normalizeRoleCode(String roleCode) {
        if (roleCode == null) {
            return null;
        }
        return roleCode.trim().toUpperCase(Locale.ROOT);
    }

    private static List<String> extractPermissionCodes(Object value) {
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            if (item == null) {
                continue;
            }
            String code = item.toString().trim();
            if (!code.isEmpty()) {
                result.add(code);
            }
        }
        return result;
    }

    private static List<String> splitPermissions(String permissions) {
        if (isBlank(permissions)) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (String item : permissions.split(",")) {
            if (!isBlank(item)) {
                list.add(item.trim());
            }
        }
        return list;
    }

    private boolean tableExists(String tableName) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Long.class, tableName);
        return count != null && count > 0;
    }

    private static List<Map<String, Object>> defaultPermissions() {
        return List.of(
                Map.of("code", "dashboard:view", "name", "查看仪表板", "module", "dashboard"),
                Map.of("code", "contract:view", "name", "查看合同", "module", "contract"),
                Map.of("code", "contract:add", "name", "新增合同", "module", "contract"),
                Map.of("code", "contract:edit", "name", "编辑合同", "module", "contract"),
                Map.of("code", "contract:delete", "name", "删除合同", "module", "contract"),
                Map.of("code", "contract:approval", "name", "合同审批", "module", "approval"),
                Map.of("code", "system:permission", "name", "权限管理", "module", "system")
        );
    }

    private static String mapRoleCodeToUserEnum(String roleCode) {
        String code = isBlank(roleCode) ? "" : roleCode.toUpperCase(Locale.ROOT);
        if (code.contains("ADMIN")) {
            return "ADMIN";
        }
        if (code.contains("MANAGER")) {
            return "MANAGER";
        }
        return "USER";
    }

    private static boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(
                authority -> "ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority())
                        || "ROLE_ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority()));
    }

    private static String normalizedBuiltinRoleName(String roleCode) {
        String code = isBlank(roleCode) ? "" : roleCode.toUpperCase(Locale.ROOT);
        return switch (code) {
            case "ADMIN", "ROLE_ADMIN" -> "系统管理员";
            case "CONTRACT_MANAGER" -> "合同管理员";
            case "APPROVAL_MANAGER" -> "审批管理员";
            case "USER", "ROLE_USER" -> "普通用户";
            default -> null;
        };
    }

    private static String normalizedBuiltinRoleDesc(String roleCode) {
        String code = isBlank(roleCode) ? "" : roleCode.toUpperCase(Locale.ROOT);
        return switch (code) {
            case "ADMIN", "ROLE_ADMIN" -> "拥有系统所有权限";
            case "CONTRACT_MANAGER" -> "合同管理相关权限";
            case "APPROVAL_MANAGER" -> "合同审批相关权限";
            case "USER", "ROLE_USER" -> "普通用户";
            default -> null;
        };
    }

    private static boolean looksMojibake(String value) {
        if (isBlank(value)) {
            return false;
        }
        // 常见 UTF-8/Latin1 混淆字符片段
        return value.contains("Ã")
                || value.contains("Â")
                || value.contains("ç")
                || value.contains("å")
                || value.contains("æ")
                || value.contains("�");
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static String nullable(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
