package com.contract.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping
    public ResponseEntity<?> getContracts(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        String listSql = """
                SELECT c.id,
                       c.id AS contractId,
                       c.contract_no AS contractNo,
                       c.contract_no AS contractNumber,
                       c.contract_name AS contractName,
                       c.contract_type AS contractType,
                       c.amount AS amount,
                       c.created_by AS createdBy,
                       DATE_FORMAT(c.created_time, '%Y-%m-%d %H:%i:%s') AS createdAt,
                       DATE_FORMAT(c.start_date, '%Y-%m-%d') AS startDate,
                       DATE_FORMAT(c.end_date, '%Y-%m-%d') AS endDate,
                       CASE c.status
                           WHEN 'DRAFT' THEN 'draft'
                           WHEN 'PENDING' THEN 'approving'
                           WHEN 'APPROVED' THEN 'active'
                           WHEN 'EXECUTING' THEN 'active'
                           WHEN 'COMPLETED' THEN 'active'
                           WHEN 'TERMINATED' THEN 'terminated'
                           ELSE 'draft'
                       END AS status
                FROM contracts c
                ORDER BY c.id DESC
                LIMIT ? OFFSET ?
                """;
        List<Map<String, Object>> records = jdbcTemplate.queryForList(listSql, safeSize, offset);

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contracts", Long.class);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        String sql = """
                SELECT c.id,
                       c.id AS contractId,
                       c.contract_no AS contractNo,
                       c.contract_no AS contractNumber,
                       c.contract_name AS contractName,
                       c.contract_type AS contractType,
                       c.amount AS amount,
                       c.created_by AS createdBy,
                       DATE_FORMAT(c.created_time, '%Y-%m-%d %H:%i:%s') AS createdAt,
                       DATE_FORMAT(c.start_date, '%Y-%m-%d') AS startDate,
                       DATE_FORMAT(c.end_date, '%Y-%m-%d') AS endDate,
                       c.description AS description,
                       c.party_a AS partyA,
                       c.party_b AS partyB,
                       CASE c.status
                           WHEN 'DRAFT' THEN 'draft'
                           WHEN 'PENDING' THEN 'approving'
                           WHEN 'APPROVED' THEN 'active'
                           WHEN 'EXECUTING' THEN 'active'
                           WHEN 'COMPLETED' THEN 'active'
                           WHEN 'TERMINATED' THEN 'terminated'
                           ELSE 'draft'
                       END AS status
                FROM contracts c
                WHERE c.id = ?
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(rows.get(0));
    }

    @GetMapping("/no/{contractNo}")
    public ResponseEntity<?> getByNo(@PathVariable String contractNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM contracts WHERE contract_no = ? LIMIT 1", contractNo);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Number id = (Number) rows.get(0).get("id");
        return getById(id.longValue());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> request) {
        String contractNo = asString(request.get("contractNo"));
        String contractName = asString(request.get("contractName"));
        if (isBlank(contractNo) || isBlank(contractName)) {
            return ResponseEntity.badRequest().body(Map.of("message", "合同编号和合同名称不能为空"));
        }
        if (existsContractNo(contractNo, null)) {
            return ResponseEntity.badRequest().body(Map.of("message", "合同编号已存在"));
        }

        String contractType = toContractType(asString(request.get("contractType")));
        BigDecimal amount = asBigDecimal(request.get("amount"));
        LocalDate startDate = asDate(request.get("startDate"));
        LocalDate endDate = asDate(request.get("endDate"));
        Long createdBy = asLong(request.get("createdBy"), 1L);
        String description = asString(request.get("description"));
        String partyName = defaultIfBlank(asString(request.get("partyName")), "未知甲方");
        String partyContact = defaultIfBlank(asString(request.get("partyContact")), "未知乙方");

        String insertSql = """
                INSERT INTO contracts (
                    contract_no, contract_name, contract_type,
                    party_a, party_b, amount, start_date, end_date,
                    status, description, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)
                """;
        jdbcTemplate.update(insertSql,
                contractNo, contractName, contractType,
                partyName, partyContact, amount, toSqlDate(startDate), toSqlDate(endDate),
                description, createdBy, createdBy);

        Long createdId = jdbcTemplate.queryForObject(
                "SELECT id FROM contracts WHERE contract_no = ? LIMIT 1", Long.class, contractNo);
        if (createdId == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "创建合同失败"));
        }
        return getById(createdId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        List<Map<String, Object>> existed = jdbcTemplate.queryForList(
                "SELECT id, contract_no FROM contracts WHERE id = ?", id);
        if (existed.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String currentNo = asString(existed.get(0).get("contract_no"));
        String contractNo = asString(request.get("contractNo"));
        if (!isBlank(contractNo) && !contractNo.equals(currentNo) && existsContractNo(contractNo, id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "合同编号已存在"));
        }

        String updateSql = """
                UPDATE contracts
                SET contract_no = COALESCE(?, contract_no),
                    contract_name = COALESCE(?, contract_name),
                    contract_type = COALESCE(?, contract_type),
                    amount = COALESCE(?, amount),
                    start_date = COALESCE(?, start_date),
                    end_date = COALESCE(?, end_date),
                    description = COALESCE(?, description),
                    party_a = COALESCE(?, party_a),
                    party_b = COALESCE(?, party_b),
                    updated_by = COALESCE(?, updated_by),
                    updated_time = NOW()
                WHERE id = ?
                """;

        String mappedType = isBlank(asString(request.get("contractType")))
                ? null
                : toContractType(asString(request.get("contractType")));

        jdbcTemplate.update(updateSql,
                nullable(contractNo),
                nullable(asString(request.get("contractName"))),
                mappedType,
                asBigDecimalOrNull(request.get("amount")),
                toSqlDate(asDate(request.get("startDate"))),
                toSqlDate(asDate(request.get("endDate"))),
                nullable(asString(request.get("description"))),
                nullable(asString(request.get("partyName"))),
                nullable(asString(request.get("partyContact"))),
                asLong(request.get("updatedBy"), null),
                id);

        return getById(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        int affected = jdbcTemplate.update("DELETE FROM contracts WHERE id = ?", id);
        if (affected == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submit(@PathVariable Long id) {
        int affected = jdbcTemplate.update(
                "UPDATE contracts SET status = 'PENDING', updated_time = NOW() WHERE id = ?", id);
        if (affected == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "提交审批成功"));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @RequestParam boolean approved,
                                     @RequestParam(required = false) String comment) {
        String nextStatus = approved ? "APPROVED" : "TERMINATED";
        int affected = jdbcTemplate.update(
                "UPDATE contracts SET status = ?, updated_time = NOW() WHERE id = ?",
                nextStatus, id);
        if (affected == 0) {
            return ResponseEntity.notFound().build();
        }
        if (!isBlank(comment)) {
            jdbcTemplate.update(
                    "UPDATE contracts SET description = CONCAT(COALESCE(description, ''), '\\n审批备注: ', ?) WHERE id = ?",
                    comment, id);
        }
        return ResponseEntity.ok(Map.of("message", approved ? "审批通过" : "审批拒绝"));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam String status) {
        String mapped = toContractDbStatus(status);
        int affected = jdbcTemplate.update(
                "UPDATE contracts SET status = ?, updated_time = NOW() WHERE id = ?",
                mapped, id);
        if (affected == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "状态更新成功"));
    }

    @GetMapping("/approvals")
    public ResponseEntity<?> getApprovals(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String approvalStatus,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        String raw = isBlank(approvalStatus) ? status : approvalStatus;
        List<String> dbStatusFilter = mapApprovalStatusFilter(raw);

        StringBuilder sql = new StringBuilder("""
                SELECT c.id,
                       c.id AS contractId,
                       c.contract_no AS contractNo,
                       c.contract_no AS contractNumber,
                       c.contract_name AS contractName,
                       c.contract_type AS contractType,
                       c.amount AS amount,
                       c.created_by AS createdBy,
                       DATE_FORMAT(c.created_time, '%Y-%m-%d %H:%i:%s') AS createdAt,
                       DATE_FORMAT(c.created_time, '%Y-%m-%d %H:%i:%s') AS applyTime,
                       c.description AS description,
                       CASE c.status
                           WHEN 'PENDING' THEN 'pending'
                           WHEN 'TERMINATED' THEN 'rejected'
                           ELSE 'approved'
                       END AS status
                FROM contracts c
                WHERE 1=1
                """);

        List<Object> params = new ArrayList<>();
        if (!dbStatusFilter.isEmpty()) {
            sql.append(" AND c.status IN (");
            for (int i = 0; i < dbStatusFilter.size(); i++) {
                if (i > 0) {
                    sql.append(",");
                }
                sql.append("?");
                params.add(dbStatusFilter.get(i));
            }
            sql.append(")");
        }
        sql.append(" ORDER BY c.id DESC LIMIT ? OFFSET ?");
        params.add(safeSize);
        params.add(offset);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM contracts c WHERE 1=1");
        List<Object> countParams = new ArrayList<>();
        if (!dbStatusFilter.isEmpty()) {
            countSql.append(" AND c.status IN (");
            for (int i = 0; i < dbStatusFilter.size(); i++) {
                if (i > 0) {
                    countSql.append(",");
                }
                countSql.append("?");
                countParams.add(dbStatusFilter.get(i));
            }
            countSql.append(")");
        }
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/expiring")
    public ResponseEntity<?> getExpiring(@RequestParam(defaultValue = "30") int days) {
        int safeDays = Math.max(days, 1);
        String sql = """
                SELECT c.id,
                       c.id AS contractId,
                       c.contract_no AS contractNo,
                       c.contract_no AS contractNumber,
                       c.contract_name AS contractName,
                       c.contract_type AS contractType,
                       c.amount AS amount,
                       DATE_FORMAT(c.end_date, '%Y-%m-%d') AS endDate
                FROM contracts c
                WHERE c.end_date IS NOT NULL
                  AND c.end_date <= DATE_ADD(CURDATE(), INTERVAL ? DAY)
                ORDER BY c.end_date ASC
                """;
        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql, safeDays);
        return ResponseEntity.ok(Map.of("records", records, "total", records.size()));
    }

    @GetMapping("/statistics/amount")
    public ResponseEntity<?> getTotalAmount(@RequestParam(required = false) String status) {
        BigDecimal total;
        if (isBlank(status)) {
            total = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM contracts", BigDecimal.class);
        } else {
            total = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(amount), 0) FROM contracts WHERE status = ?",
                    BigDecimal.class,
                    toContractDbStatus(status));
        }
        return ResponseEntity.ok(Map.of("totalAmount", total == null ? BigDecimal.ZERO : total));
    }

    @GetMapping("/check-unique")
    public ResponseEntity<?> checkUnique(@RequestParam String contractNo,
                                         @RequestParam(required = false) Long excludeId) {
        return ResponseEntity.ok(Map.of("unique", !existsContractNo(contractNo, excludeId)));
    }

    private boolean existsContractNo(String contractNo, Long excludeId) {
        if (isBlank(contractNo)) {
            return false;
        }
        String sql = excludeId == null
                ? "SELECT COUNT(*) FROM contracts WHERE contract_no = ?"
                : "SELECT COUNT(*) FROM contracts WHERE contract_no = ? AND id <> ?";
        Long count = excludeId == null
                ? jdbcTemplate.queryForObject(sql, Long.class, contractNo)
                : jdbcTemplate.queryForObject(sql, Long.class, contractNo, excludeId);
        return count != null && count > 0;
    }

    private static List<String> mapApprovalStatusFilter(String raw) {
        if (isBlank(raw)) {
            return Collections.emptyList();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : raw.split(",")) {
            String key = item.trim().toUpperCase(Locale.ROOT);
            switch (key) {
                case "PENDING" -> result.add("PENDING");
                case "APPROVED" -> {
                    result.add("APPROVED");
                    result.add("EXECUTING");
                    result.add("COMPLETED");
                }
                case "REJECTED" -> result.add("TERMINATED");
                default -> {
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static String toContractDbStatus(String status) {
        if (isBlank(status)) {
            return "DRAFT";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "approving", "pending" -> "PENDING";
            case "active", "approved", "executing", "completed" -> "APPROVED";
            case "terminated", "rejected" -> "TERMINATED";
            default -> "DRAFT";
        };
    }

    private static String toContractType(String type) {
        if (isBlank(type)) {
            return "OTHER";
        }
        String upper = type.toUpperCase(Locale.ROOT);
        if (upper.contains("销售") || "SALES".equals(upper)) {
            return "SALES";
        }
        if (upper.contains("采购") || "PURCHASE".equals(upper)) {
            return "PURCHASE";
        }
        if (upper.contains("服务") || "SERVICE".equals(upper) || upper.contains("技术")) {
            return "SERVICE";
        }
        return "OTHER";
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal asBigDecimalOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate asDate(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long asLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullable(String value) {
        return isBlank(value) ? null : value;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }
}
