package com.contract.controller;

import com.contract.service.ContractExportService;
import com.contract.service.ContractImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private static final Pattern CONTRACT_TYPE_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,50}$");
    private static final Pattern ENUM_ITEM_PATTERN = Pattern.compile("'([^']*)'");
    private static final Pattern SIGNING_YEAR_PATTERN = Pattern.compile("20\\d{2}");
    private static final long MAX_ATTACHMENT_SIZE_BYTES = 100L * 1024L * 1024L;
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContractExportService contractExportService;

    @Autowired
    private ContractImportService contractImportService;

    @Value("${app.upload-dir:/app/uploads}")
    private String uploadDir;

    @GetMapping
    public ResponseEntity<?> getContracts(@RequestParam(defaultValue = "1", name = "page") int page,
                                          @RequestParam(defaultValue = "10", name = "size") int size,
                                          @RequestParam(required = false, name = "keyword") String keyword,
                                          @RequestParam(required = false, name = "customerName") String customerName,
                                          @RequestParam(required = false, name = "contractType") String contractType,
                                          @RequestParam(required = false, name = "signingYear") Integer signingYear,
                                          @RequestParam(required = false, name = "signingYears") String signingYears,
                                          @RequestParam(required = false, name = "sortBy") String sortBy,
                                          @RequestParam(required = false, name = "sortOrder") String sortOrder,
                                          @RequestParam(required = false, name = "status") String status,
                                          @RequestParam(required = false, name = "startDate") String startDate,
                                          @RequestParam(required = false, name = "endDate") String endDate) {
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
                       'Vince Gao' AS createdBy,
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
            params.add(normalizeContractTypeCode(contractType));
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
            countParams.add(normalizeContractTypeCode(contractType));
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
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        result.put("page", safePage);
        result.put("size", safeSize);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/signing-years")
    public ResponseEntity<?> getSigningYears() {
        List<Integer> records = jdbcTemplate.queryForList("""
                SELECT DISTINCT signing_year
                FROM contracts
                WHERE signing_year IS NOT NULL
                ORDER BY signing_year DESC
                """, Integer.class);
        return ResponseEntity.ok(Map.of("records", records, "total", records.size()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportContracts(@RequestParam(required = false) String fields,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate) {
        List<String> selectedFields = parseExportFields(fields);
        List<Map<String, Object>> records = queryContractsForExport(keyword, status, startDate, endDate);
        byte[] fileContent = contractExportService.exportToExcel(records, selectedFields);

        String fileName = "合同导出.xlsx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .header(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(fileContent);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:write')")
    public ResponseEntity<?> importContracts(@RequestPart("file") MultipartFile file,
                                             @RequestParam(defaultValue = "false") boolean overwrite) {
        final List<ContractImportService.ImportRow> rows;
        try {
            rows = contractImportService.parseContractRows(file);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        if (rows.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Excel中没有可导入的数据"));
        }

        int total = rows.size();
        int success = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<Map<String, Object>> errors = new ArrayList<>();

        for (ContractImportService.ImportRow importRow : rows) {
            int rowNumber = importRow.rowNumber();
            Map<String, Object> values = importRow.values();
            try {
                String contractNo = asString(values.get("contractNo"));
                String contractName = asString(values.get("contractName"));
                if (isBlank(contractNo)) {
                    contractNo = generateImportContractNo(rowNumber);
                }
                if (isBlank(contractName)) {
                    contractName = "批量导入合同-第" + rowNumber + "行";
                }

                String contractType = toContractType(asString(values.get("contractType")));
                BigDecimal taxRate = asBigDecimalOrNull(values.get("taxRate"));
                if (taxRate == null) {
                    taxRate = BigDecimal.ZERO;
                }
                Integer signingYear = extractSigningYearFromContractNo(contractNo);
                BigDecimal amount = asBigDecimalOrNull(values.get("amount"));
                LocalDate startDate = asDate(values.get("startDate"));
                LocalDate endDate = asDate(values.get("endDate"));
                if (amount == null) {
                    amount = BigDecimal.ZERO;
                }
                if (startDate == null) {
                    startDate = LocalDate.now();
                }
                if (endDate == null) {
                    endDate = startDate.plusYears(1);
                }

                String dbStatus = toContractDbStatus(asString(values.get("status")));
                Long createdBy = asLong(values.get("createdBy"), 1L);
                String partyA = defaultIfBlank(asString(values.get("customerName")), "导入甲方");
                String partyB = defaultIfBlank(asString(values.get("companySignatory")), "导入乙方");
                String description = asString(values.get("description"));

                Long existingId = findContractIdByNo(contractNo);
                if (existingId != null) {
                    if (!overwrite) {
                        skipped++;
                        continue;
                    }
                    jdbcTemplate.update("""
                                    UPDATE contracts
                                    SET contract_name = ?,
                                        contract_type = ?,
                                        signing_year = ?,
                                        tax_rate = ?,
                                        amount = ?,
                                        start_date = ?,
                                        end_date = ?,
                                        status = ?,
                                        description = COALESCE(?, description),
                                        updated_by = ?,
                                        updated_time = NOW()
                                    WHERE id = ?
                                    """,
                            contractName, contractType, signingYear, taxRate, amount,
                            toSqlDate(startDate), toSqlDate(endDate),
                            dbStatus, description, createdBy, existingId);
                    updated++;
                    continue;
                }

                jdbcTemplate.update("""
                                INSERT INTO contracts (
                                    contract_no, contract_name, contract_type, signing_year, tax_rate,
                                    party_a, party_b, amount, start_date, end_date,
                                    status, description, created_by, updated_by
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        contractNo, contractName, contractType, signingYear, taxRate,
                        partyA, partyB, amount,
                        toSqlDate(startDate), toSqlDate(endDate), dbStatus, description, createdBy, createdBy);
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(Map.of("row", rowNumber, "message", defaultIfBlank(e.getMessage(), "导入失败")));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("overwrite", overwrite);
        result.put("errors", errors.size() > 20 ? errors.subList(0, 20) : errors);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/attachments")
    public ResponseEntity<?> getAttachments(@PathVariable Long id) {
        if (!existsContractId(id)) {
            return ResponseEntity.notFound().build();
        }
        ensureContractAttachmentTable();
        return ResponseEntity.ok(Map.of("records", queryAttachmentRecords(id)));
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:write')")
    public ResponseEntity<?> uploadAttachments(@PathVariable Long id,
                                               @RequestPart("files") MultipartFile[] files,
                                               @RequestParam(required = false) String description,
                                               Authentication authentication) {
        if (!existsContractId(id)) {
            return ResponseEntity.notFound().build();
        }
        if (files == null || files.length == 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "请至少上传一个附件"));
        }
        ensureContractAttachmentTable();

        Long uploadUserId = resolveCurrentUserId(authentication);
        Path contractDir = Paths.get(uploadDir, "contracts", String.valueOf(id)).normalize();
        try {
            Files.createDirectories(contractDir);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "创建附件目录失败"));
        }

        int uploaded = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
                errors.add(Map.of("file", defaultIfBlank(file.getOriginalFilename(), "unknown"), "message", "单个文件不能超过100MB"));
                continue;
            }
            String originName = safeFileName(file.getOriginalFilename());
            if (isBlank(originName)) {
                errors.add(Map.of("file", "unknown", "message", "文件名无效"));
                continue;
            }
            if (!isAllowedAttachmentExtension(originName)) {
                errors.add(Map.of("file", originName, "message", "文件类型不支持"));
                continue;
            }
            String extension = "";
            int dotIndex = originName.lastIndexOf('.');
            if (dotIndex >= 0) {
                extension = originName.substring(dotIndex);
            }
            String storedName = UUID.randomUUID().toString().replace("-", "") + extension;
            Path targetFile = contractDir.resolve(storedName).normalize();
            try {
                file.transferTo(targetFile.toFile());
                if (hasColumn("contract_attachments", "attachment_name")) {
                    jdbcTemplate.update("""
                                    INSERT INTO contract_attachments (
                                        tenant_id, contract_id, attachment_name, file_path, file_size, file_type,
                                        attachment_type, description, uploader_id, deleted_flag
                                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                                    """,
                            0L,
                            id,
                            originName,
                            targetFile.toString(),
                            file.getSize(),
                            nullable(file.getContentType()),
                            "CONTRACT",
                            nullable(description),
                            uploadUserId);
                } else {
                    jdbcTemplate.update("""
                                    INSERT INTO contract_attachments (
                                        contract_id, file_name, file_path, file_size, file_type, upload_user_id, description
                                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                                    """,
                            id,
                            originName,
                            targetFile.toString(),
                            file.getSize(),
                            nullable(file.getContentType()),
                            uploadUserId,
                            nullable(description));
                }
                uploaded++;
            } catch (Exception e) {
                errors.add(Map.of("file", originName, "message", defaultIfBlank(e.getMessage(), "上传失败")));
            }
        }

        if (uploaded == 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "附件上传失败",
                    "uploaded", 0,
                    "errors", errors
            ));
        }
        Map<String, Object> response = new HashMap<>();
        response.put("uploaded", uploaded);
        response.put("errors", errors);
        response.put("records", queryAttachmentRecords(id));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<?> downloadAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        ensureContractAttachmentTable();
        List<Map<String, Object>> rows;
        if (hasColumn("contract_attachments", "attachment_name")) {
            rows = jdbcTemplate.queryForList(
                    """
                            SELECT attachment_name AS fileName, file_path, file_type, file_size
                            FROM contract_attachments
                            WHERE id = ? AND contract_id = ? AND COALESCE(deleted_flag, 0) = 0
                            LIMIT 1
                            """,
                    attachmentId, id);
        } else {
            rows = jdbcTemplate.queryForList(
                    """
                            SELECT file_name AS fileName, file_path, file_type, file_size
                            FROM contract_attachments
                            WHERE id = ? AND contract_id = ?
                            LIMIT 1
                            """,
                    attachmentId, id);
        }
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> row = rows.get(0);
        String filePath = asString(row.get("file_path"));
        String fileName = asString(row.get("fileName"));
        if (isBlank(filePath)) {
            return ResponseEntity.notFound().build();
        }
        Path path = Paths.get(filePath).normalize();
        Resource resource = new FileSystemResource(path.toFile());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = asString(row.get("file_type"));
        if (isBlank(contentType)) {
            try {
                contentType = Files.probeContentType(path);
            } catch (IOException e) {
                contentType = null;
            }
        }
        if (isBlank(contentType)) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        String encodedFileName = URLEncoder.encode(defaultIfBlank(fileName, path.getFileName().toString()),
                StandardCharsets.UTF_8).replace("+", "%20");
        long contentLength;
        try {
            contentLength = resource.contentLength();
        } catch (IOException e) {
            contentLength = -1;
        }
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .header(HttpHeaders.CONTENT_TYPE, contentType);
        if (contentLength >= 0) {
            builder.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));
        }
        return builder.body(resource);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:write')")
    public ResponseEntity<?> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        ensureContractAttachmentTable();
        List<Map<String, Object>> rows;
        boolean legacySchema = hasColumn("contract_attachments", "attachment_name");
        if (legacySchema) {
            rows = jdbcTemplate.queryForList(
                    """
                            SELECT file_path
                            FROM contract_attachments
                            WHERE id = ? AND contract_id = ? AND COALESCE(deleted_flag, 0) = 0
                            LIMIT 1
                            """,
                    attachmentId, id);
        } else {
            rows = jdbcTemplate.queryForList(
                    """
                            SELECT file_path
                            FROM contract_attachments
                            WHERE id = ? AND contract_id = ?
                            LIMIT 1
                            """,
                    attachmentId, id);
        }
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String filePath = asString(rows.get(0).get("file_path"));
        int affected;
        if (legacySchema) {
            affected = jdbcTemplate.update(
                    """
                            UPDATE contract_attachments
                            SET deleted_flag = 1, updated_at = NOW()
                            WHERE id = ? AND contract_id = ?
                            """,
                    attachmentId, id);
        } else {
            affected = jdbcTemplate.update(
                    "DELETE FROM contract_attachments WHERE id = ? AND contract_id = ?",
                    attachmentId, id);
        }
        if (affected == 0) {
            return ResponseEntity.notFound().build();
        }

        if (!isBlank(filePath)) {
            try {
                Files.deleteIfExists(Paths.get(filePath).normalize());
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.ok(Map.of("message", "附件删除成功"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        String sql = """
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
                       'Vince Gao' AS createdBy,
                       DATE_FORMAT(c.created_time, '%Y-%m-%d %H:%i:%s') AS createdAt,
                       DATE_FORMAT(c.start_date, '%Y-%m-%d') AS startDate,
                       DATE_FORMAT(c.end_date, '%Y-%m-%d') AS endDate,
                       c.description AS description,
                       c.party_a AS partyA,
                       c.party_b AS partyB,
                       c.party_a AS customerName,
                       c.party_b AS companySignatory,
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:write')")
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
        Integer signingYear = extractSigningYearFromContractNo(contractNo);
        BigDecimal taxRate = asBigDecimalOrNull(request.get("taxRate"));
        if (taxRate == null) {
            taxRate = BigDecimal.ZERO;
        }
        BigDecimal amount = asBigDecimal(request.get("amount"));
        LocalDate startDate = asDate(request.get("startDate"));
        LocalDate endDate = asDate(request.get("endDate"));
        Long createdBy = asLong(request.get("createdBy"), 1L);
        String description = asString(request.get("description"));
        String partyName = defaultIfBlank(asString(request.get("partyName")), "未知甲方");
        String partyContact = defaultIfBlank(asString(request.get("partyContact")), "未知乙方");

        String insertSql = """
                INSERT INTO contracts (
                    contract_no, contract_name, contract_type, signing_year, tax_rate,
                    party_a, party_b, amount, start_date, end_date,
                    status, description, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?)
                """;
        jdbcTemplate.update(insertSql,
                contractNo, contractName, contractType, signingYear, taxRate,
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:write')")
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
        String finalContractNo = isBlank(contractNo) ? currentNo : contractNo;
        Integer signingYear = extractSigningYearFromContractNo(finalContractNo);

        String updateSql = """
                UPDATE contracts
                SET contract_no = COALESCE(?, contract_no),
                    contract_name = COALESCE(?, contract_name),
                    contract_type = COALESCE(?, contract_type),
                    signing_year = ?,
                    tax_rate = COALESCE(?, tax_rate),
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
                signingYear,
                asBigDecimalOrNull(request.get("taxRate")),
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:delete')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        int affected = jdbcTemplate.update("DELETE FROM contracts WHERE id = ?", id);
        if (affected == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:approval')")
    public ResponseEntity<?> submit(@PathVariable Long id) {
        int affected = jdbcTemplate.update(
                "UPDATE contracts SET status = 'PENDING', updated_time = NOW() WHERE id = ?", id);
        if (affected == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "提交审批成功"));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_APPROVAL_MANAGER') or hasAuthority('contract:approval')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_APPROVAL_MANAGER') or hasAuthority('contract:approval')")
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
                       c.signing_year AS signingYear,
                       c.contract_name AS contractName,
                       c.party_a AS customerName,
                       c.party_b AS companySignatory,
                       c.contract_type AS contractType,
                       c.amount AS amount,
                       'Vince Gao' AS createdBy,
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
                       c.signing_year AS signingYear,
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

    @GetMapping("/statistics/overview")
    public ResponseEntity<?> getOverview(@RequestParam(required = false, name = "year") Integer year) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS totalContracts,
                       SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS approvingContracts,
                       SUM(CASE WHEN status IN ('APPROVED', 'EXECUTING', 'COMPLETED') THEN 1 ELSE 0 END) AS activeContracts,
                       SUM(CASE WHEN contract_type = 'SALES' THEN COALESCE(amount, 0) ELSE 0 END) AS salesRevenue,
                       SUM(CASE WHEN contract_type = 'PURCHASE' THEN COALESCE(amount, 0) ELSE 0 END) AS purchaseCost,
                       SUM(CASE WHEN created_time >= DATE_FORMAT(CURDATE(), '%Y-%m-01')
                                 AND created_time < DATE_ADD(LAST_DAY(CURDATE()), INTERVAL 1 DAY)
                                THEN 1 ELSE 0 END) AS newThisMonth
                FROM contracts
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (year != null) {
            sql.append(" AND signing_year = ?");
            params.add(year);
        }
        Map<String, Object> row = jdbcTemplate.queryForMap(sql.toString(), params.toArray());
        Map<String, Object> result = new HashMap<>();
        result.put("totalContracts", asInt(row.get("totalContracts")));
        result.put("approvingContracts", asInt(row.get("approvingContracts")));
        result.put("activeContracts", asInt(row.get("activeContracts")));
        result.put("salesRevenue", asBigDecimal(row.get("salesRevenue")));
        result.put("purchaseCost", asBigDecimal(row.get("purchaseCost")));
        result.put("newThisMonth", asInt(row.get("newThisMonth")));

        ensureContractTypeMetaTable();
        Map<String, String> typeNameMap = getContractTypeMetaNames();
        StringBuilder typeCountSql = new StringBuilder("""
                SELECT contract_type AS code, COUNT(*) AS count
                FROM contracts
                WHERE 1=1
                """);
        List<Object> typeParams = new ArrayList<>();
        if (year != null) {
            typeCountSql.append(" AND signing_year = ?");
            typeParams.add(year);
        }
        typeCountSql.append(" GROUP BY contract_type ORDER BY count DESC, code ASC");
        List<Map<String, Object>> typeRows = jdbcTemplate.queryForList(typeCountSql.toString(), typeParams.toArray());
        List<Map<String, Object>> contractTypeStats = new ArrayList<>();
        for (Map<String, Object> typeRow : typeRows) {
            String code = normalizeContractTypeCode(asString(typeRow.get("code")));
            int count = asInt(typeRow.get("count"));
            if (isBlank(code)) {
                continue;
            }
            contractTypeStats.add(Map.of(
                    "code", code,
                    "name", typeNameMap.getOrDefault(code, defaultContractTypeName(code)),
                    "count", count
            ));
        }
        result.put("contractTypeStats", contractTypeStats);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/check-unique")
    public ResponseEntity<?> checkUnique(@RequestParam String contractNo,
                                         @RequestParam(required = false) Long excludeId) {
        return ResponseEntity.ok(Map.of("unique", !existsContractNo(contractNo, excludeId)));
    }

    @GetMapping("/types")
    public ResponseEntity<?> getContractTypes() {
        ensureContractTypeMetaTable();
        List<String> enumCodes = getContractTypeEnumCodes();
        Map<String, String> metaNames = getContractTypeMetaNames();
        List<Map<String, Object>> records = new ArrayList<>();
        for (String code : enumCodes) {
            String name = metaNames.getOrDefault(code, defaultContractTypeName(code));
            records.add(Map.of("code", code, "name", name));
        }
        return ResponseEntity.ok(Map.of("records", records, "total", records.size()));
    }

    @PostMapping("/types")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:write')")
    public ResponseEntity<?> createContractType(@RequestBody Map<String, Object> request) {
        ensureContractTypeMetaTable();
        String code = normalizeContractTypeCode(asString(request.get("code")));
        String name = normalizeContractTypeName(asString(request.get("name")), code);
        if (!isValidContractTypeCode(code)) {
            return ResponseEntity.badRequest().body(Map.of("message", "类型编码仅支持2-50位大写字母、数字、下划线"));
        }

        List<String> enumCodes = getContractTypeEnumCodes();
        if (enumCodes.contains(code)) {
            return ResponseEntity.badRequest().body(Map.of("message", "合同类型编码已存在"));
        }

        List<String> updatedCodes = new ArrayList<>(enumCodes);
        updatedCodes.add(code);
        updateContractTypeEnum(updatedCodes);
        upsertContractTypeMeta(code, name);
        return ResponseEntity.ok(Map.of("code", code, "name", name));
    }

    @PutMapping("/types/{code}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:write')")
    public ResponseEntity<?> updateContractType(@PathVariable String code,
                                                @RequestBody Map<String, Object> request) {
        ensureContractTypeMetaTable();
        String oldCode = normalizeContractTypeCode(code);
        String newCode = normalizeContractTypeCode(asString(request.get("code")));
        if (isBlank(newCode)) {
            newCode = oldCode;
        }
        String newName = normalizeContractTypeName(asString(request.get("name")), newCode);

        if (!isValidContractTypeCode(oldCode) || !isValidContractTypeCode(newCode)) {
            return ResponseEntity.badRequest().body(Map.of("message", "类型编码仅支持2-50位大写字母、数字、下划线"));
        }

        List<String> enumCodes = getContractTypeEnumCodes();
        if (!enumCodes.contains(oldCode)) {
            return ResponseEntity.badRequest().body(Map.of("message", "合同类型不存在"));
        }

        if (!oldCode.equals(newCode) && enumCodes.contains(newCode)) {
            return ResponseEntity.badRequest().body(Map.of("message", "新的合同类型编码已存在"));
        }

        if (!oldCode.equals(newCode)) {
            List<String> updatedCodes = new ArrayList<>();
            for (String item : enumCodes) {
                updatedCodes.add(item.equals(oldCode) ? newCode : item);
            }
            updateContractTypeEnum(updatedCodes);
            jdbcTemplate.update("UPDATE contracts SET contract_type = ? WHERE contract_type = ?", newCode, oldCode);
            jdbcTemplate.update("DELETE FROM contract_type_meta WHERE type_code = ?", oldCode);
        }

        upsertContractTypeMeta(newCode, newName);
        return ResponseEntity.ok(Map.of("code", newCode, "name", newName));
    }

    @DeleteMapping("/types/{code}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:write')")
    public ResponseEntity<?> deleteContractType(@PathVariable String code) {
        ensureContractTypeMetaTable();
        String typeCode = normalizeContractTypeCode(code);
        if (!isValidContractTypeCode(typeCode)) {
            return ResponseEntity.badRequest().body(Map.of("message", "合同类型编码格式不正确"));
        }

        List<String> enumCodes = getContractTypeEnumCodes();
        if (!enumCodes.contains(typeCode)) {
            return ResponseEntity.badRequest().body(Map.of("message", "合同类型不存在"));
        }

        Long usedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contracts WHERE contract_type = ?", Long.class, typeCode);
        if (usedCount != null && usedCount > 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "该类型已被合同使用，无法删除"));
        }

        List<String> updatedCodes = new ArrayList<>();
        for (String item : enumCodes) {
            if (!item.equals(typeCode)) {
                updatedCodes.add(item);
            }
        }
        if (updatedCodes.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "至少保留一个合同类型"));
        }
        updateContractTypeEnum(updatedCodes);
        jdbcTemplate.update("DELETE FROM contract_type_meta WHERE type_code = ?", typeCode);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
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
        String upper = type.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("销售") || "SALES".equals(upper)) {
            return "SALES";
        }
        if (upper.contains("采购") || "PURCHASE".equals(upper)) {
            return "PURCHASE";
        }
        if (upper.contains("服务") || "SERVICE".equals(upper) || upper.contains("技术")) {
            return "SERVICE";
        }
        if (CONTRACT_TYPE_CODE_PATTERN.matcher(upper).matches()) {
            return upper;
        }
        return "OTHER";
    }

    private void ensureContractAttachmentTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS contract_attachments (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    tenant_id BIGINT DEFAULT 0,
                    contract_id BIGINT NOT NULL,
                    attachment_name VARCHAR(200) NOT NULL,
                    file_path VARCHAR(500) NOT NULL,
                    file_size BIGINT NOT NULL,
                    file_type VARCHAR(100),
                    attachment_type VARCHAR(50),
                    description VARCHAR(500),
                    uploader_id BIGINT NOT NULL DEFAULT 1,
                    deleted_flag TINYINT DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_contract_id (contract_id),
                    INDEX idx_uploader_id (uploader_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private List<Map<String, Object>> queryAttachmentRecords(Long contractId) {
        if (hasColumn("contract_attachments", "attachment_name")) {
            return jdbcTemplate.queryForList("""
                    SELECT id,
                           attachment_name AS name,
                           file_size AS size,
                           file_type AS fileType,
                           DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS uploadTime
                    FROM contract_attachments
                    WHERE contract_id = ?
                      AND COALESCE(deleted_flag, 0) = 0
                    ORDER BY created_at DESC, id DESC
                    """, contractId);
        }
        return jdbcTemplate.queryForList("""
                SELECT id,
                       file_name AS name,
                       file_size AS size,
                       file_type AS fileType,
                       DATE_FORMAT(created_time, '%Y-%m-%d %H:%i:%s') AS uploadTime
                FROM contract_attachments
                WHERE contract_id = ?
                ORDER BY created_time DESC, id DESC
                """, contractId);
    }

    private boolean existsContractId(Long id) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contracts WHERE id = ?", Long.class, id);
        return count != null && count > 0;
    }

    private static String safeFileName(String fileName) {
        if (isBlank(fileName)) {
            return null;
        }
        String normalized = fileName.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        String trimmed = normalized.trim();
        if (trimmed.contains("..") || trimmed.contains("/") || trimmed.contains("\0")) {
            return null;
        }
        return trimmed;
    }

    private static boolean isAllowedAttachmentExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return false;
        }
        String ext = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return ALLOWED_ATTACHMENT_EXTENSIONS.contains(ext);
    }

    private boolean hasColumn(String tableName, String columnName) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Long.class, tableName, columnName);
        return count != null && count > 0;
    }

    private void ensureContractTypeMetaTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS contract_type_meta (
                    type_code VARCHAR(50) NOT NULL PRIMARY KEY,
                    type_name VARCHAR(100) NOT NULL,
                    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private Map<String, String> getContractTypeMetaNames() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT type_code, type_name FROM contract_type_meta");
        Map<String, String> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String code = normalizeContractTypeCode(asString(row.get("type_code")));
            String name = asString(row.get("type_name"));
            if (!isBlank(code) && !isBlank(name)) {
                result.put(code, name.trim());
            }
        }
        return result;
    }

    private List<String> getContractTypeEnumCodes() {
        String enumSql = """
                SELECT COLUMN_TYPE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'contracts'
                  AND COLUMN_NAME = 'contract_type'
                LIMIT 1
                """;
        String columnType = jdbcTemplate.queryForObject(enumSql, String.class);
        if (columnType == null || !columnType.toLowerCase(Locale.ROOT).startsWith("enum(")) {
            return new ArrayList<>(List.of("SALES", "PURCHASE", "SERVICE", "OTHER"));
        }
        List<String> values = new ArrayList<>();
        Matcher matcher = ENUM_ITEM_PATTERN.matcher(columnType);
        while (matcher.find()) {
            String value = normalizeContractTypeCode(matcher.group(1));
            if (!isBlank(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private void updateContractTypeEnum(List<String> typeCodes) {
        StringBuilder sql = new StringBuilder("ALTER TABLE contracts MODIFY COLUMN contract_type ENUM(");
        for (int i = 0; i < typeCodes.size(); i++) {
            if (i > 0) {
                sql.append(",");
            }
            sql.append("'").append(typeCodes.get(i)).append("'");
        }
        sql.append(") NOT NULL");
        jdbcTemplate.execute(sql.toString());
    }

    private void upsertContractTypeMeta(String code, String name) {
        jdbcTemplate.update("""
                        INSERT INTO contract_type_meta (type_code, type_name)
                        VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE type_name = VALUES(type_name)
                        """,
                code, name);
    }

    private static String normalizeContractTypeCode(String code) {
        if (isBlank(code)) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeContractTypeName(String name, String fallbackCode) {
        if (!isBlank(name)) {
            return name.trim();
        }
        return defaultContractTypeName(fallbackCode);
    }

    private static boolean isValidContractTypeCode(String code) {
        return !isBlank(code) && CONTRACT_TYPE_CODE_PATTERN.matcher(code).matches();
    }

    private static String defaultContractTypeName(String code) {
        if (isBlank(code)) {
            return "其他";
        }
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "SALES" -> "销售合同";
            case "PURCHASE" -> "采购合同";
            case "SERVICE" -> "服务合同";
            case "OTHER" -> "其他";
            default -> code;
        };
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

    private static Date toSqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
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

    private static String nullable(String value) {
        return isBlank(value) ? null : value;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
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

    private Long findContractIdByNo(String contractNo) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM contracts WHERE contract_no = ? LIMIT 1", contractNo);
        if (rows.isEmpty()) {
            return null;
        }
        Number number = (Number) rows.get(0).get("id");
        return number == null ? null : number.longValue();
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return 1L;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE username = ? LIMIT 1",
                authentication.getName()
        );
        if (rows.isEmpty()) {
            return 1L;
        }
        Object value = rows.get(0).get("id");
        return asLong(value, 1L);
    }

    private String generateImportContractNo(int rowNumber) {
        return "IMP" + System.currentTimeMillis() + rowNumber + (int) (Math.random() * 1000);
    }

    private static Integer extractSigningYearFromContractNo(String contractNo) {
        if (isBlank(contractNo)) {
            return null;
        }
        Matcher matcher = SIGNING_YEAR_PATTERN.matcher(contractNo);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> parseExportFields(String fields) {
        if (isBlank(fields)) {
            return Collections.emptyList();
        }
        List<String> selected = new ArrayList<>();
        for (String item : fields.split(",")) {
            String value = item == null ? "" : item.trim();
            if (!value.isEmpty()) {
                selected.add(value);
            }
        }
        return selected;
    }

    private List<Map<String, Object>> queryContractsForExport(String keyword,
                                                              String status,
                                                              String startDate,
                                                              String endDate) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id,
                       c.id AS contractId,
                       c.contract_no AS contractNo,
                       c.contract_no AS contractNumber,
                       c.signing_year AS signingYear,
                       c.contract_name AS contractName,
                       c.contract_type AS contractType,
                       c.amount AS amount,
                       'Vince Gao' AS createdBy,
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
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (!isBlank(keyword)) {
            sql.append(" AND (c.contract_no LIKE ? OR c.contract_name LIKE ?)");
            String likeKeyword = "%" + keyword.trim() + "%";
            params.add(likeKeyword);
            params.add(likeKeyword);
        }
        if (!isBlank(status)) {
            sql.append(" AND c.status = ?");
            params.add(toContractDbStatus(status));
        }
        if (!isBlank(startDate)) {
            sql.append(" AND c.start_date >= ?");
            params.add(startDate);
        }
        if (!isBlank(endDate)) {
            sql.append(" AND c.end_date <= ?");
            params.add(endDate);
        }

        sql.append(" ORDER BY c.id DESC");
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }
}
