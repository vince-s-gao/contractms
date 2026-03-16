package com.contract.controller;

import com.contract.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/system")
@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN') or hasAuthority('system:permission')")
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
        boolean loadedFromDb = false;
        if (tableExists("permissions")) {
            records = jdbcTemplate.queryForList("""
                    SELECT permission_code AS code,
                           permission_name AS name,
                           COALESCE(resource_type, '') AS module
                    FROM permissions
                    WHERE COALESCE(status, 'ACTIVE') IN ('ACTIVE', '1')
                    ORDER BY permission_code ASC
                    """);
            loadedFromDb = true;
        } else {
            records = new ArrayList<>();
        }
        if (records.isEmpty()) {
            records = defaultPermissions();
        }
        records = mergeBuiltinPermissions(records, loadedFromDb);
        records = normalizePermissionRecords(records, loadedFromDb);
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
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> seed : builtinPermissionSeeds()) {
            result.add(Map.of(
                    "code", seed.get("code"),
                    "name", seed.get("name"),
                    "module", seed.get("module")
            ));
        }
        return result;
    }

    private List<Map<String, Object>> mergeBuiltinPermissions(List<Map<String, Object>> records, boolean loadedFromDb) {
        LinkedHashMap<String, Map<String, Object>> merged = new LinkedHashMap<>();
        for (Map<String, Object> row : records) {
            String code = normalizePermissionCode(asString(row.get("code")));
            if (isBlank(code)) {
                continue;
            }
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            copy.put("code", code);
            copy.put("name", asString(row.get("name")));
            copy.put("module", asString(row.get("module")));
            merged.put(code, copy);
        }

        for (Map<String, Object> seed : builtinPermissionSeeds()) {
            String code = asString(seed.get("code"));
            if (!merged.containsKey(code)) {
                merged.put(code, new LinkedHashMap<>(Map.of(
                        "code", code,
                        "name", seed.get("name"),
                        "module", seed.get("module")
                )));
                if (loadedFromDb) {
                    insertPermissionSeed(seed);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    private void insertPermissionSeed(Map<String, Object> seed) {
        String code = asString(seed.get("code"));
        String name = asString(seed.get("name"));
        String module = asString(seed.get("module"));
        String path = asString(seed.get("path"));
        String desc = asString(seed.get("description"));
        int sortOrder = asInt(seed.get("sortOrder"));
        if (isBlank(code) || isBlank(name)) {
            return;
        }
        try {
            jdbcTemplate.update("""
                            INSERT INTO permissions (
                                permission_code, permission_name, resource_type, resource_path,
                                description, parent_id, sort_order, status
                            )
                            SELECT ?, ?, ?, ?, ?, 0, ?, 'ACTIVE'
                            WHERE NOT EXISTS (
                                SELECT 1 FROM permissions WHERE permission_code = ?
                            )
                            """,
                    code, name, module, nullable(path), nullable(desc), sortOrder, code);
        } catch (Exception ignored) {
            // 忽略历史库结构差异导致的插入失败，接口返回会使用内存合并后的权限列表
        }
    }

    private static List<Map<String, Object>> builtinPermissionSeeds() {
        List<Map<String, Object>> seeds = new ArrayList<>();
        seeds.add(permissionSeed("USER_VIEW", "查看用户", "MENU", "/users", "查看用户列表", 1));
        seeds.add(permissionSeed("USER_CREATE", "创建用户", "BUTTON", "/users", "创建新用户", 2));
        seeds.add(permissionSeed("USER_EDIT", "编辑用户", "BUTTON", "/users", "编辑用户信息", 3));
        seeds.add(permissionSeed("USER_DELETE", "删除用户", "BUTTON", "/users", "删除用户", 4));

        seeds.add(permissionSeed("CONTRACT_VIEW", "查看合同", "MENU", "/contracts", "查看合同列表", 5));
        seeds.add(permissionSeed("CONTRACT_CREATE", "新建合同", "BUTTON", "/contracts", "新建合同", 6));
        seeds.add(permissionSeed("CONTRACT_EDIT", "编辑合同", "BUTTON", "/contracts", "编辑合同信息", 7));
        seeds.add(permissionSeed("CONTRACT_DELETE", "删除合同", "BUTTON", "/contracts", "删除合同", 8));
        seeds.add(permissionSeed("CONTRACT_APPROVE", "审批合同", "BUTTON", "/approval", "审批合同", 9));
        seeds.add(permissionSeed("CONTRACT_BATCH_UPLOAD", "批量上传合同", "BUTTON", "/contracts/import", "批量上传合同", 10));
        seeds.add(permissionSeed("CONTRACT_EXPORT", "导出合同", "BUTTON", "/contracts/export", "导出合同", 11));
        seeds.add(permissionSeed("CONTRACT_TYPE_MANAGE", "合同类型管理", "BUTTON", "/contracts/types", "管理合同类型", 12));

        seeds.add(permissionSeed("APPROVAL_VIEW", "查看审批", "MENU", "/approval", "查看审批任务", 13));
        seeds.add(permissionSeed("APPROVAL_PROCESS", "处理审批", "BUTTON", "/approval", "处理审批任务", 14));

        seeds.add(permissionSeed("FILE_UPLOAD", "文件上传", "BUTTON", "/files", "上传文件", 15));
        seeds.add(permissionSeed("FILE_DOWNLOAD", "文件下载", "BUTTON", "/files", "下载文件", 16));
        seeds.add(permissionSeed("FILE_DELETE", "文件删除", "BUTTON", "/files", "删除文件", 17));

        seeds.add(permissionSeed("DASHBOARD:VIEW", "查看仪表板", "MENU", "/dashboard", "查看仪表板", 18));
        seeds.add(permissionSeed("SYSTEM:PERMISSION", "权限管理", "MENU", "/permissions", "权限管理", 19));
        return seeds;
    }

    private static Map<String, Object> permissionSeed(String code, String name, String module, String path, String description, int sortOrder) {
        LinkedHashMap<String, Object> seed = new LinkedHashMap<>();
        seed.put("code", code);
        seed.put("name", name);
        seed.put("module", module);
        seed.put("path", path);
        seed.put("description", description);
        seed.put("sortOrder", sortOrder);
        return seed;
    }

    private static String normalizePermissionCode(String permissionCode) {
        if (isBlank(permissionCode)) {
            return null;
        }
        return permissionCode.trim().toUpperCase(Locale.ROOT);
    }

    private List<Map<String, Object>> normalizePermissionRecords(List<Map<String, Object>> records, boolean loadedFromDb) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : records) {
            String code = asString(row.get("code"));
            String rawName = asString(row.get("name"));
            String module = asString(row.get("module"));

            String fixedName = normalizePermissionName(code, rawName);
            LinkedHashMap<String, Object> fixedRow = new LinkedHashMap<>();
            fixedRow.put("code", code);
            fixedRow.put("name", fixedName);
            fixedRow.put("module", isBlank(module) ? "" : module);
            normalized.add(fixedRow);

            if (loadedFromDb && !isBlank(code) && !Objects.equals(rawName, fixedName)) {
                jdbcTemplate.update("UPDATE permissions SET permission_name = ? WHERE permission_code = ?", fixedName, code);
            }
        }
        return normalized;
    }

    private static String normalizePermissionName(String code, String rawName) {
        if (!isBlank(rawName) && !looksMojibake(rawName)) {
            return rawName.trim();
        }

        String decoded = decodeLatin1Utf8(rawName);
        if (!isBlank(decoded) && !looksMojibake(decoded)) {
            return decoded;
        }

        String builtin = normalizedBuiltinPermissionName(code);
        if (!isBlank(builtin)) {
            return builtin;
        }

        if (!isBlank(decoded)) {
            return decoded;
        }
        if (!isBlank(rawName)) {
            return rawName.trim();
        }
        return isBlank(code) ? "未命名权限" : code.trim();
    }

    private static String decodeLatin1Utf8(String value) {
        if (isBlank(value)) {
            return value;
        }
        try {
            return new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return value.trim();
        }
    }

    private static String normalizedBuiltinPermissionName(String permissionCode) {
        String code = isBlank(permissionCode) ? "" : permissionCode.trim().toUpperCase(Locale.ROOT);
        return switch (code) {
            case "APPROVAL_PROCESS", "CONTRACT:APPROVAL" -> "处理审批";
            case "APPROVAL_VIEW" -> "查看审批";
            case "CONTRACT_APPROVE" -> "审批合同";
            case "CONTRACT_CREATE", "CONTRACT:ADD" -> "创建合同";
            case "CONTRACT_BATCH_UPLOAD", "CONTRACT:IMPORT" -> "批量上传合同";
            case "CONTRACT_EXPORT", "CONTRACT:EXPORT" -> "导出合同";
            case "CONTRACT_TYPE_MANAGE", "CONTRACT:TYPE_MANAGE" -> "合同类型管理";
            case "CONTRACT_DELETE", "CONTRACT:DELETE" -> "删除合同";
            case "CONTRACT_EDIT", "CONTRACT:EDIT" -> "编辑合同";
            case "CONTRACT_VIEW", "CONTRACT:VIEW" -> "查看合同";
            case "FILE_DELETE" -> "文件删除";
            case "FILE_DOWNLOAD" -> "文件下载";
            case "FILE_UPLOAD" -> "文件上传";
            case "USER_CREATE", "USER:WRITE" -> "创建用户";
            case "USER_DELETE" -> "删除用户";
            case "USER_EDIT" -> "编辑用户";
            case "USER_VIEW", "USER:READ" -> "查看用户";
            case "SYSTEM:PERMISSION" -> "权限管理";
            case "DASHBOARD:VIEW" -> "查看仪表板";
            default -> null;
        };
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
                authority -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(authority.getAuthority())
                        || "ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority())
                        || "ROLE_ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority())
                        || "system:permission".equalsIgnoreCase(authority.getAuthority()));
    }

    private static String normalizedBuiltinRoleName(String roleCode) {
        String code = isBlank(roleCode) ? "" : roleCode.toUpperCase(Locale.ROOT);
        return switch (code) {
            case "SUPER_ADMIN", "ROLE_SUPER_ADMIN" -> "超级管理员";
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
            case "SUPER_ADMIN", "ROLE_SUPER_ADMIN" -> "拥有平台全部管理权限";
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

    private static int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
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
