package com.contract.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class OperationLogService {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean tableEnsured = new AtomicBoolean(false);

    public OperationLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(Authentication authentication,
                    String operationType,
                    String module,
                    String description,
                    String status,
                    String errorMessage) {
        try {
            ensureOperationLogTable();
            String opType = defaultIfBlank(operationType, "UNKNOWN");
            if ("QUERY".equalsIgnoreCase(opType)) {
                return;
            }
            String opModule = defaultIfBlank(module, "SYSTEM");
            String opDesc = defaultIfBlank(description, "");
            String opStatus = defaultIfBlank(status, "SUCCESS");

            HttpServletRequest request = currentRequest();
            String requestMethod = request == null ? null : request.getMethod();
            String requestUrl = request == null ? null : request.getRequestURI();
            String ipAddress = request == null ? null : resolveIp(request);
            String userAgent = request == null ? null : request.getHeader("User-Agent");
            String username = resolveUsername(authentication);
            Long userId = resolveUserId(authentication);

            if (hasColumn("operation_logs", "module")) {
                jdbcTemplate.update("""
                                INSERT INTO operation_logs (
                                    tenant_id, user_id, username, operation_type, module, description,
                                    request_method, request_url, ip_address, user_agent, status, error_message
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        0L,
                        userId,
                        nullable(username),
                        opType,
                        opModule,
                        nullable(opDesc),
                        nullable(requestMethod),
                        nullable(requestUrl),
                        nullable(ipAddress),
                        nullable(userAgent),
                        opStatus,
                        nullable(errorMessage)
                );
                return;
            }

            // 兼容旧表结构
            jdbcTemplate.update("""
                            INSERT INTO operation_logs (
                                user_id, operation_type, operation_target, operation_content, ip_address, user_agent
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    userId == null ? 1L : userId,
                    opType,
                    opModule,
                    nullable(opDesc),
                    nullable(ipAddress),
                    nullable(userAgent)
            );
        } catch (Exception ignored) {
            // 操作日志不应阻断主流程
        }
    }

    public Map<String, Object> queryLogs(String keyword,
                                         String username,
                                         String module,
                                         String operationType,
                                         String status,
                                         String startTime,
                                         String endTime,
                                         int page,
                                         int size) {
        ensureOperationLogTable();
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int offset = (safePage - 1) * safeSize;

        if (hasColumn("operation_logs", "module")) {
            return queryModernLogs(keyword, username, module, operationType, status, startTime, endTime, safePage, safeSize, offset);
        }
        return queryLegacyLogs(keyword, username, module, operationType, startTime, endTime, safePage, safeSize, offset);
    }

    private Map<String, Object> queryModernLogs(String keyword,
                                                String username,
                                                String module,
                                                String operationType,
                                                String status,
                                                String startTime,
                                                String endTime,
                                                int page,
                                                int size,
                                                int offset) {
        StringBuilder listSql = new StringBuilder("""
                SELECT id,
                       COALESCE(username, '') AS username,
                       COALESCE(module, '') AS module,
                       COALESCE(operation_type, '') AS operationType,
                       COALESCE(description, '') AS description,
                       COALESCE(status, 'SUCCESS') AS status,
                       COALESCE(ip_address, '') AS ipAddress,
                       COALESCE(request_method, '') AS requestMethod,
                       COALESCE(request_url, '') AS requestUrl,
                       COALESCE(error_message, '') AS errorMessage,
                       created_at AS operationTime
                FROM operation_logs
                WHERE 1=1
                """);
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM operation_logs WHERE 1=1");
        List<Object> params = new ArrayList<>();
        List<Object> countParams = new ArrayList<>();

        appendModernFilters(listSql, params, keyword, username, module, operationType, status, startTime, endTime);
        appendModernFilters(countSql, countParams, keyword, username, module, operationType, status, startTime, endTime);

        listSql.append(" ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        List<Map<String, Object>> records = jdbcTemplate.query(listSql.toString(), (rs, rowNum) -> toLogRecord(rs), params.toArray());
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page", page,
                "size", size
        );
    }

    private Map<String, Object> queryLegacyLogs(String keyword,
                                                String username,
                                                String module,
                                                String operationType,
                                                String startTime,
                                                String endTime,
                                                int page,
                                                int size,
                                                int offset) {
        StringBuilder listSql = new StringBuilder("""
                SELECT ol.id,
                       COALESCE(u.username, '') AS username,
                       COALESCE(ol.operation_target, '') AS module,
                       COALESCE(ol.operation_type, '') AS operationType,
                       COALESCE(ol.operation_content, '') AS description,
                       'SUCCESS' AS status,
                       COALESCE(ol.ip_address, '') AS ipAddress,
                       '' AS requestMethod,
                       '' AS requestUrl,
                       '' AS errorMessage,
                       ol.operation_time AS operationTime
                FROM operation_logs ol
                LEFT JOIN users u ON u.id = ol.user_id
                WHERE 1=1
                """);
        StringBuilder countSql = new StringBuilder("""
                SELECT COUNT(*)
                FROM operation_logs ol
                LEFT JOIN users u ON u.id = ol.user_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        List<Object> countParams = new ArrayList<>();

        appendLegacyFilters(listSql, params, keyword, username, module, operationType, startTime, endTime);
        appendLegacyFilters(countSql, countParams, keyword, username, module, operationType, startTime, endTime);

        listSql.append(" ORDER BY ol.operation_time DESC, ol.id DESC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        List<Map<String, Object>> records = jdbcTemplate.query(listSql.toString(), (rs, rowNum) -> toLogRecord(rs), params.toArray());
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
        return Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page", page,
                "size", size
        );
    }

    private void appendModernFilters(StringBuilder sql,
                                     List<Object> params,
                                     String keyword,
                                     String username,
                                     String module,
                                     String operationType,
                                     String status,
                                     String startTime,
                                     String endTime) {
        if (!isBlank(keyword)) {
            sql.append(" AND (description LIKE ? OR request_url LIKE ? OR operation_type LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (!isBlank(username)) {
            sql.append(" AND username LIKE ?");
            params.add("%" + username.trim() + "%");
        }
        if (!isBlank(module)) {
            sql.append(" AND module = ?");
            params.add(module.trim());
        }
        if (!isBlank(operationType)) {
            sql.append(" AND operation_type = ?");
            params.add(operationType.trim());
        }
        if (!isBlank(status)) {
            sql.append(" AND status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (!isBlank(startTime)) {
            sql.append(" AND created_at >= ?");
            params.add(startTime.trim());
        }
        if (!isBlank(endTime)) {
            sql.append(" AND created_at <= ?");
            params.add(endTime.trim());
        }
    }

    private void appendLegacyFilters(StringBuilder sql,
                                     List<Object> params,
                                     String keyword,
                                     String username,
                                     String module,
                                     String operationType,
                                     String startTime,
                                     String endTime) {
        if (!isBlank(keyword)) {
            sql.append(" AND (ol.operation_content LIKE ? OR ol.operation_target LIKE ? OR ol.operation_type LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (!isBlank(username)) {
            sql.append(" AND u.username LIKE ?");
            params.add("%" + username.trim() + "%");
        }
        if (!isBlank(module)) {
            sql.append(" AND ol.operation_target = ?");
            params.add(module.trim());
        }
        if (!isBlank(operationType)) {
            sql.append(" AND ol.operation_type = ?");
            params.add(operationType.trim());
        }
        if (!isBlank(startTime)) {
            sql.append(" AND ol.operation_time >= ?");
            params.add(startTime.trim());
        }
        if (!isBlank(endTime)) {
            sql.append(" AND ol.operation_time <= ?");
            params.add(endTime.trim());
        }
    }

    private Map<String, Object> toLogRecord(ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> item = new HashMap<>();
        item.put("id", rs.getLong("id"));
        item.put("username", rs.getString("username"));
        item.put("module", rs.getString("module"));
        item.put("operationType", rs.getString("operationType"));
        item.put("description", rs.getString("description"));
        item.put("status", rs.getString("status"));
        item.put("ipAddress", rs.getString("ipAddress"));
        item.put("requestMethod", rs.getString("requestMethod"));
        item.put("requestUrl", rs.getString("requestUrl"));
        item.put("errorMessage", rs.getString("errorMessage"));
        Timestamp ts = rs.getTimestamp("operationTime");
        item.put("operationTime", formatToChinaTime(ts));
        return item;
    }

    private String formatToChinaTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        // operation_logs 时间由 UTC 数据库写入，这里统一转换为东八区展示
        LocalDateTime chinaTime = timestamp.toLocalDateTime()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(DISPLAY_ZONE)
                .toLocalDateTime();
        return chinaTime.format(DATETIME_FORMATTER);
    }

    private void ensureOperationLogTable() {
        if (tableEnsured.compareAndSet(false, true)) {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS operation_logs (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        tenant_id BIGINT DEFAULT 0,
                        user_id BIGINT NULL,
                        username VARCHAR(50) NULL,
                        operation_type VARCHAR(50) NOT NULL,
                        module VARCHAR(50) NOT NULL,
                        description VARCHAR(500) NULL,
                        request_method VARCHAR(10) NULL,
                        request_url VARCHAR(500) NULL,
                        request_params TEXT NULL,
                        response_result TEXT NULL,
                        ip_address VARCHAR(45) NULL,
                        user_agent TEXT NULL,
                        status VARCHAR(20) NOT NULL,
                        error_message TEXT NULL,
                        execution_time BIGINT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        INDEX idx_user_id (user_id),
                        INDEX idx_operation_type (operation_type),
                        INDEX idx_module (module),
                        INDEX idx_status (status),
                        INDEX idx_created_at (created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                    """);
        }
    }

    private boolean hasColumn(String tableName, String columnName) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM information_schema.COLUMNS
                            WHERE TABLE_SCHEMA = DATABASE()
                              AND TABLE_NAME = ?
                              AND COLUMN_NAME = ?
                            """,
                    Long.class,
                    tableName,
                    columnName
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Long resolveUserId(Authentication authentication) {
        String username = resolveUsername(authentication);
        if (isBlank(username)) {
            return 1L;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id FROM users WHERE username = ? LIMIT 1", username);
            if (rows.isEmpty()) {
                return 1L;
            }
            Object value = rows.get(0).get("id");
            if (value instanceof Number n) {
                return n.longValue();
            }
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return 1L;
        }
    }

    private String resolveUsername(Authentication authentication) {
        Authentication auth = authentication;
        if (auth == null) {
            auth = SecurityContextHolder.getContext().getAuthentication();
        }
        if (auth != null && !isBlank(auth.getName()) && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return auth.getName();
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private static String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (!isBlank(xff)) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (!isBlank(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String nullable(String value) {
        return isBlank(value) ? null : value;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
