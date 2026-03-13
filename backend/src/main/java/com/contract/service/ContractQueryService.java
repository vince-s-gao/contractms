package com.contract.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ContractQueryService {

    private final JdbcTemplate jdbcTemplate;

    public ContractQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> queryContracts(int page,
                                              int size,
                                              String keyword,
                                              String customerName,
                                              String contractType,
                                              Integer signingYear,
                                              String signingYears,
                                              String sortBy,
                                              String sortOrder,
                                              String status,
                                              String startDate,
                                              String endDate) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        StringBuilder listSql = new StringBuilder("""
                SELECT c.id,
                       c.id AS contractId,
                       c.contract_no AS contractNo,
                       c.contract_no AS contractNumber,
                       c.signing_year AS signingYear,
                       c.contract_name AS contractName,
                       c.party_a AS customerName,
                       c.party_b AS companySignatory,
                       c.contract_type AS contractType,
                       c.amount AS amount,
                       COALESCE(c.tax_rate, 0) AS taxRate,
                       ROUND(
                           CASE
                               WHEN COALESCE(c.tax_rate, 0) = 0 THEN 0
                               ELSE COALESCE(c.amount, 0) * COALESCE(c.tax_rate, 0) / (100 + COALESCE(c.tax_rate, 0))
                           END,
                           2
                       ) AS taxAmount,
                       ROUND(
                           CASE
                               WHEN COALESCE(c.tax_rate, 0) = 0 THEN COALESCE(c.amount, 0)
                               ELSE COALESCE(c.amount, 0) / (1 + COALESCE(c.tax_rate, 0) / 100)
                           END,
                           2
                       ) AS amountWithoutTax,
                       COALESCE(NULLIF(u.real_name, ''), NULLIF(u.username, ''), CONCAT('用户#', c.created_by)) AS createdBy,
                       DATE_FORMAT(CONVERT_TZ(c.created_time, '+00:00', '+08:00'), '%Y-%m-%d %H:%i:%s') AS createdAt,
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
                LEFT JOIN users u ON u.id = c.created_by
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (!isBlank(keyword)) {
            listSql.append(" AND (c.contract_no LIKE ? OR c.contract_name LIKE ? OR c.party_a LIKE ?)");
            String likeKeyword = "%" + keyword.trim() + "%";
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
        }
        if (!isBlank(customerName)) {
            listSql.append(" AND c.party_a LIKE ?");
            params.add("%" + customerName.trim() + "%");
        }
        if (!isBlank(contractType)) {
            listSql.append(" AND c.contract_type = ?");
            params.add(contractType.trim().toUpperCase(Locale.ROOT));
        }
        List<Integer> signingYearList = parseSigningYears(signingYears);
        if (!signingYearList.isEmpty()) {
            listSql.append(" AND c.signing_year IN (");
            appendPlaceholders(listSql, signingYearList.size());
            listSql.append(")");
            params.addAll(signingYearList);
        } else if (signingYear != null) {
            listSql.append(" AND c.signing_year = ?");
            params.add(signingYear);
        }
        if (!isBlank(status)) {
            listSql.append(" AND c.status = ?");
            params.add(toContractDbStatus(status));
        }
        if (!isBlank(startDate)) {
            listSql.append(" AND c.start_date >= ?");
            params.add(startDate);
        }
        if (!isBlank(endDate)) {
            listSql.append(" AND c.end_date <= ?");
            params.add(endDate);
        }
        String orderByColumn = resolveContractSortColumn(sortBy);
        String orderByDirection = resolveSortDirection(sortOrder);
        if (orderByColumn == null) {
            listSql.append(" ORDER BY COALESCE(c.updated_time, c.created_time) DESC, c.id DESC");
        } else {
            listSql.append(" ORDER BY ").append(orderByColumn).append(" ").append(orderByDirection).append(", c.id DESC");
        }
        listSql.append(" LIMIT ? OFFSET ?");
        params.add(safeSize);
        params.add(offset);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(listSql.toString(), params.toArray());

        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM contracts c WHERE 1=1");
        List<Object> countParams = new ArrayList<>();
        if (!isBlank(keyword)) {
            countSql.append(" AND (c.contract_no LIKE ? OR c.contract_name LIKE ? OR c.party_a LIKE ?)");
            String likeKeyword = "%" + keyword.trim() + "%";
            countParams.add(likeKeyword);
            countParams.add(likeKeyword);
            countParams.add(likeKeyword);
        }
        if (!isBlank(customerName)) {
            countSql.append(" AND c.party_a LIKE ?");
            countParams.add("%" + customerName.trim() + "%");
        }
        if (!isBlank(contractType)) {
            countSql.append(" AND c.contract_type = ?");
            countParams.add(contractType.trim().toUpperCase(Locale.ROOT));
        }
        if (!signingYearList.isEmpty()) {
            countSql.append(" AND c.signing_year IN (");
            appendPlaceholders(countSql, signingYearList.size());
            countSql.append(")");
            countParams.addAll(signingYearList);
        } else if (signingYear != null) {
            countSql.append(" AND c.signing_year = ?");
            countParams.add(signingYear);
        }
        if (!isBlank(status)) {
            countSql.append(" AND c.status = ?");
            countParams.add(toContractDbStatus(status));
        }
        if (!isBlank(startDate)) {
            countSql.append(" AND c.start_date >= ?");
            countParams.add(startDate);
        }
        if (!isBlank(endDate)) {
            countSql.append(" AND c.end_date <= ?");
            countParams.add(endDate);
        }

        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String resolveSortDirection(String sortOrder) {
        if ("asc".equalsIgnoreCase(sortOrder)) {
            return "ASC";
        }
        return "DESC";
    }

    private static String resolveContractSortColumn(String sortBy) {
        if (isBlank(sortBy)) {
            return null;
        }
        return switch (sortBy.trim()) {
            case "contractNumber", "contractNo" -> "c.contract_no";
            case "signingYear" -> "c.signing_year";
            case "contractName" -> "c.contract_name";
            case "customerName" -> "c.party_a";
            case "companySignatory" -> "c.party_b";
            case "contractType" -> "c.contract_type";
            case "amount" -> "c.amount";
            case "status" -> "c.status";
            case "startDate" -> "c.start_date";
            case "endDate" -> "c.end_date";
            case "createdBy" -> "c.created_by";
            case "createdAt" -> "c.created_time";
            default -> null;
        };
    }

    private static void appendPlaceholders(StringBuilder sql, int size) {
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sql.append(",");
            }
            sql.append("?");
        }
    }

    private static List<Integer> parseSigningYears(String signingYears) {
        if (isBlank(signingYears)) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>();
        for (String item : signingYears.split(",")) {
            if (isBlank(item)) {
                continue;
            }
            try {
                result.add(Integer.parseInt(item.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    private static String toContractDbStatus(String status) {
        if (isBlank(status)) {
            return "DRAFT";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "approving", "pending" -> "PENDING";
            case "active", "approved" -> "APPROVED";
            case "executing" -> "EXECUTING";
            case "completed" -> "COMPLETED";
            case "terminated", "rejected" -> "TERMINATED";
            default -> "DRAFT";
        };
    }
}
