package com.contract.controller;

import com.contract.service.ContractExportService;
import com.contract.service.ContractImportService;
import com.contract.service.ContractQueryService;
import com.contract.service.OperationLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.UncategorizedSQLException;
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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
    private static final List<DateTimeFormatter> FLEXIBLE_DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy/M/dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy年M月d日")
    );
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ContractExportService contractExportService;

    @Autowired
    private ContractImportService contractImportService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired
    private ContractQueryService contractQueryService;

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
        return ResponseEntity.ok(contractQueryService.queryContracts(
                page, size, keyword, customerName, contractType, signingYear, signingYears,
                sortBy, sortOrder, status, startDate, endDate
        ));
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
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAnyAuthority('CONTRACT_EXPORT','contract:write','contract:read')")
    public ResponseEntity<byte[]> exportContracts(@RequestParam(required = false) String fields,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(required = false) String customerName,
                                                  @RequestParam(required = false) String contractType,
                                                  @RequestParam(required = false) String signingYears,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String startDate,
                                                  @RequestParam(required = false) String endDate,
                                                  Authentication authentication) {
        List<String> selectedFields = parseExportFields(fields);
        List<Map<String, Object>> records = queryContractsForExport(
                keyword, customerName, contractType, signingYears, status, startDate, endDate);
        byte[] fileContent = contractExportService.exportToExcel(records, selectedFields);
        operationLogService.log(authentication, "EXPORT", "CONTRACT",
                "导出合同成功，导出条数=" + records.size(), "SUCCESS", null);

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
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAnyAuthority('CONTRACT_BATCH_UPLOAD','contract:write')")
    public ResponseEntity<?> importContracts(@RequestPart("file") MultipartFile file,
                                             @RequestParam(defaultValue = "false") boolean overwrite,
                                             Authentication authentication) {
        final List<ContractImportService.ImportRow> rows;
        try {
            rows = contractImportService.parseContractRows(file);
        } catch (IllegalArgumentException e) {
            operationLogService.log(authentication, "IMPORT", "CONTRACT",
                    "批量导入合同失败：文件解析失败", "FAILED", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
        if (rows.isEmpty()) {
            operationLogService.log(authentication, "IMPORT", "CONTRACT",
                    "批量导入合同失败：无有效数据", "FAILED", "Excel中没有可导入的数据");
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
                ensureContractTypeAvailable(contractType);
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
                                        party_a = ?,
                                        party_b = ?,
                                        amount = ?,
                                        start_date = ?,
                                        end_date = ?,
                                        status = ?,
                                        description = COALESCE(?, description),
                                        updated_by = ?,
                                        updated_time = NOW()
                                    WHERE id = ?
                                    """,
                            contractName, contractType, signingYear, taxRate, partyA, partyB, amount,
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
        operationLogService.log(authentication, "IMPORT", "CONTRACT",
                "批量导入完成：total=" + total + ", success=" + success + ", updated=" + updated + ", failed=" + failed,
                failed > 0 ? "FAILED" : "SUCCESS",
                failed > 0 ? "部分记录导入失败" : null);
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
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAnyAuthority('FILE_UPLOAD','contract:write')")
    public ResponseEntity<?> uploadAttachments(@PathVariable Long id,
                                               @RequestPart("files") MultipartFile[] files,
                                               @RequestParam(required = false) String description,
                                               Authentication authentication) {
        if (!existsContractId(id)) {
            operationLogService.log(authentication, "UPLOAD_ATTACHMENT", "CONTRACT",
                    "上传附件失败：合同不存在 id=" + id, "FAILED", "合同不存在");
            return ResponseEntity.notFound().build();
        }
        if (files == null || files.length == 0) {
            operationLogService.log(authentication, "UPLOAD_ATTACHMENT", "CONTRACT",
                    "上传附件失败：未选择文件，contractId=" + id, "FAILED", "请至少上传一个附件");
            return ResponseEntity.badRequest().body(Map.of("message", "请至少上传一个附件"));
        }
        ensureContractAttachmentTable();

        Long uploadUserId = resolveCurrentUserId(authentication);
        Path contractDir = Paths.get(uploadDir, "contracts", String.valueOf(id)).normalize();
        try {
            Files.createDirectories(contractDir);
        } catch (IOException e) {
            operationLogService.log(authentication, "UPLOAD_ATTACHMENT", "CONTRACT",
                    "上传附件失败：创建目录失败，contractId=" + id, "FAILED", e.getMessage());
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
            operationLogService.log(authentication, "UPLOAD_ATTACHMENT", "CONTRACT",
                    "上传附件失败：全部文件失败，contractId=" + id, "FAILED", "附件上传失败");
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
        operationLogService.log(authentication, "UPLOAD_ATTACHMENT", "CONTRACT",
                "上传附件成功：contractId=" + id + "，uploaded=" + uploaded, "SUCCESS", null);
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
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAnyAuthority('FILE_DELETE','contract:write')")
    public ResponseEntity<?> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId, Authentication authentication) {
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
            operationLogService.log(authentication, "DELETE_ATTACHMENT", "CONTRACT",
                    "删除附件失败：附件不存在 contractId=" + id + ", attachmentId=" + attachmentId, "FAILED", "附件不存在");
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
            operationLogService.log(authentication, "DELETE_ATTACHMENT", "CONTRACT",
                    "删除附件失败：附件不存在 contractId=" + id + ", attachmentId=" + attachmentId, "FAILED", "附件不存在");
            return ResponseEntity.notFound().build();
        }

        if (!isBlank(filePath)) {
            try {
                Files.deleteIfExists(Paths.get(filePath).normalize());
            } catch (Exception ignored) {
            }
        }
        operationLogService.log(authentication, "DELETE_ATTACHMENT", "CONTRACT",
                "删除附件成功 contractId=" + id + ", attachmentId=" + attachmentId, "SUCCESS", null);
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
                       COALESCE(NULLIF(u.real_name, ''), NULLIF(u.username, ''), CONCAT('用户#', c.created_by)) AS createdBy,
                       DATE_FORMAT(CONVERT_TZ(c.created_time, '+00:00', '+08:00'), '%Y-%m-%d %H:%i:%s') AS createdAt,
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
                LEFT JOIN users u ON u.id = c.created_by
                WHERE c.id = ?
                """;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, id);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("participants", queryParticipantRecords(id));
        return ResponseEntity.ok(result);
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
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAnyAuthority('CONTRACT_CREATE','contract:write')")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> request, Authentication authentication) {
        String contractNo = asString(request.get("contractNo"));
        String contractName = asString(request.get("contractName"));
        if (isBlank(contractNo) || isBlank(contractName)) {
            operationLogService.log(authentication, "CREATE", "CONTRACT",
                    "创建合同失败：合同编号或名称为空", "FAILED", "合同编号和合同名称不能为空");
            return ResponseEntity.badRequest().body(Map.of("message", "合同编号和合同名称不能为空"));
        }
        if (existsContractNo(contractNo, null)) {
            operationLogService.log(authentication, "CREATE", "CONTRACT",
                    "创建合同失败：合同编号已存在 " + contractNo, "FAILED", "合同编号已存在");
            return ResponseEntity.badRequest().body(Map.of("message", "合同编号已存在"));
        }

        String contractType = toContractType(asString(request.get("contractType")));
        ensureContractTypeAvailable(contractType);
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
        String partyName = defaultIfBlank(
                firstNonBlank(asString(request.get("customerName")), asString(request.get("partyName"))),
                "未知甲方");
        String partyContact = defaultIfBlank(
                firstNonBlank(asString(request.get("companySignatory")), asString(request.get("partyContact"))),
                "未知乙方");

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
            operationLogService.log(authentication, "CREATE", "CONTRACT",
                    "创建合同失败：创建后未找到合同ID contractNo=" + contractNo, "FAILED", "创建合同失败");
            return ResponseEntity.badRequest().body(Map.of("message", "创建合同失败"));
        }
        saveParticipants(createdId, request.get("participants"), createdBy);
        operationLogService.log(authentication, "CREATE", "CONTRACT",
                "创建合同成功 contractNo=" + contractNo + ", id=" + createdId, "SUCCESS", null);
        return getById(createdId);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAnyAuthority('CONTRACT_EDIT','contract:write')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> request, Authentication authentication) {
        List<Map<String, Object>> existed = jdbcTemplate.queryForList(
                "SELECT id, contract_no, status FROM contracts WHERE id = ?", id);
        if (existed.isEmpty()) {
            operationLogService.log(authentication, "UPDATE", "CONTRACT",
                    "更新合同失败：合同不存在 id=" + id, "FAILED", "合同不存在");
            return ResponseEntity.notFound().build();
        }

        String currentNo = asString(existed.get(0).get("contract_no"));
        String currentStatus = asString(existed.get(0).get("status"));
        boolean isAdmin = isAdmin(authentication);
        if (!isAdmin && !"DRAFT".equalsIgnoreCase(currentStatus)) {
            operationLogService.log(authentication, "UPDATE", "CONTRACT",
                    "更新合同失败：非管理员编辑非草稿合同 id=" + id + ", status=" + currentStatus, "FAILED", "仅管理员可编辑已审批合同");
            return ResponseEntity.status(403).body(Map.of("message", "仅管理员可编辑已审批合同"));
        }
        String contractNo = asString(request.get("contractNo"));
        if (!isBlank(contractNo) && !contractNo.equals(currentNo) && existsContractNo(contractNo, id)) {
            operationLogService.log(authentication, "UPDATE", "CONTRACT",
                    "更新合同失败：合同编号已存在 " + contractNo, "FAILED", "合同编号已存在");
            return ResponseEntity.badRequest().body(Map.of("message", "合同编号已存在"));
        }
        String finalContractNo = isBlank(contractNo) ? currentNo : contractNo;
        Integer signingYear = extractSigningYearFromContractNo(finalContractNo);
        String resetStatus = isAdmin && isApprovedOrRejectedStatus(currentStatus) ? "DRAFT" : null;

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
                    status = COALESCE(?, status),
                    updated_by = COALESCE(?, updated_by),
                    updated_time = NOW()
                WHERE id = ?
                """;

        String mappedType = isBlank(asString(request.get("contractType")))
                ? null
                : toContractType(asString(request.get("contractType")));
        if (!isBlank(mappedType)) {
            ensureContractTypeAvailable(mappedType);
        }

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
                nullable(firstNonBlank(asString(request.get("customerName")), asString(request.get("partyName")))),
                nullable(firstNonBlank(asString(request.get("companySignatory")), asString(request.get("partyContact")))),
                resetStatus,
                asLong(request.get("updatedBy"), null),
                id);
        saveParticipants(id, request.get("participants"), resolveCurrentUserId(authentication));
        operationLogService.log(authentication, "UPDATE", "CONTRACT",
                "更新合同成功 id=" + id + ", contractNo=" + finalContractNo
                        + (resetStatus == null ? "" : ", statusResetTo=DRAFT"),
                "SUCCESS", null);
        return getById(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:delete')")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        int affected = jdbcTemplate.update("DELETE FROM contracts WHERE id = ?", id);
        if (affected == 0) {
            operationLogService.log(authentication, "DELETE", "CONTRACT",
                    "删除合同失败：合同不存在 id=" + id, "FAILED", "合同不存在");
            return ResponseEntity.notFound().build();
        }
        operationLogService.log(authentication, "DELETE", "CONTRACT",
                "删除合同成功 id=" + id, "SUCCESS", null);
        return ResponseEntity.ok(Map.of("message", "删除成功"));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAuthority('contract:approval')")
    public ResponseEntity<?> submit(@PathVariable Long id, Authentication authentication) {
        int affected = jdbcTemplate.update(
                "UPDATE contracts SET status = 'PENDING', updated_time = NOW() WHERE id = ?", id);
        if (affected == 0) {
            operationLogService.log(authentication, "SUBMIT_APPROVAL", "CONTRACT",
                    "提交审批失败：合同不存在 id=" + id, "FAILED", "合同不存在");
            return ResponseEntity.notFound().build();
        }
        Long submitterId = resolveCurrentUserId(authentication);
        jdbcTemplate.update("""
                        INSERT INTO approval_records (
                            contract_id, approver_id, approval_result, approval_opinion, approval_time, next_approver_id
                        ) VALUES (?, ?, 0, '提交审批', NOW(), NULL)
                        """,
                id, submitterId);
        operationLogService.log(authentication, "SUBMIT_APPROVAL", "CONTRACT",
                "提交审批成功 id=" + id, "SUCCESS", null);
        return ResponseEntity.ok(Map.of("message", "提交审批成功"));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_APPROVAL_MANAGER') or hasAuthority('contract:approval')")
    public ResponseEntity<?> approve(@PathVariable Long id,
                                     @RequestParam boolean approved,
                                     @RequestParam(required = false) String comment,
                                     Authentication authentication) {
        String nextStatus = approved ? "APPROVED" : "TERMINATED";
        int affected = jdbcTemplate.update(
                "UPDATE contracts SET status = ?, updated_time = NOW() WHERE id = ?",
                nextStatus, id);
        if (affected == 0) {
            operationLogService.log(authentication, approved ? "APPROVE" : "REJECT", "CONTRACT",
                    "审批失败：合同不存在 id=" + id, "FAILED", "合同不存在");
            return ResponseEntity.notFound().build();
        }
        if (!isBlank(comment)) {
            jdbcTemplate.update(
                    "UPDATE contracts SET description = CONCAT(COALESCE(description, ''), '\\n审批备注: ', ?) WHERE id = ?",
                    comment, id);
        }
        Long approverId = resolveCurrentUserId(authentication);
        jdbcTemplate.update("""
                        INSERT INTO approval_records (
                            contract_id, approver_id, approval_result, approval_opinion, approval_time, next_approver_id
                        ) VALUES (?, ?, ?, ?, NOW(), NULL)
                        """,
                id, approverId, approved ? 1 : 2, nullable(comment));
        operationLogService.log(authentication, approved ? "APPROVE" : "REJECT", "CONTRACT",
                (approved ? "审批通过" : "审批拒绝") + " id=" + id, "SUCCESS", null);
        return ResponseEntity.ok(Map.of("message", approved ? "审批通过" : "审批拒绝"));
    }

    @GetMapping("/{id}/approval-records")
    public ResponseEntity<?> getApprovalRecords(@PathVariable Long id) {
        List<Map<String, Object>> contractRows = jdbcTemplate.queryForList("""
                SELECT status,
                       COALESCE(description, '') AS description,
                       DATE_FORMAT(CONVERT_TZ(COALESCE(updated_time, created_time), '+00:00', '+08:00'), '%Y-%m-%d %H:%i:%s') AS fallbackTime
                FROM contracts
                WHERE id = ?
                LIMIT 1
                """, id);
        if (contractRows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
                SELECT ar.id,
                       COALESCE(u.real_name, u.username, '系统') AS approver,
                       CASE ar.approval_result
                           WHEN 0 THEN 'pending'
                           WHEN 1 THEN 'approved'
                           WHEN 2 THEN 'rejected'
                           ELSE 'pending'
                       END AS status,
                       COALESCE(ar.approval_opinion, '') AS comment,
                       DATE_FORMAT(CONVERT_TZ(ar.approval_time, '+00:00', '+08:00'), '%Y-%m-%d %H:%i:%s') AS createdAt
                FROM approval_records ar
                LEFT JOIN users u ON u.id = ar.approver_id
                WHERE ar.contract_id = ?
                ORDER BY ar.approval_time DESC, ar.id DESC
                """, id);
        if (records.isEmpty()) {
            String contractStatus = asString(contractRows.get(0).get("status"));
            String fallbackStatus = switch (contractStatus == null ? "" : contractStatus.toUpperCase(Locale.ROOT)) {
                case "PENDING" -> "pending";
                case "TERMINATED" -> "rejected";
                case "APPROVED", "EXECUTING", "COMPLETED" -> "approved";
                default -> null;
            };
            if (!isBlank(fallbackStatus)) {
                String fallbackComment = "历史合同未记录审批留痕";
                records = List.of(Map.of(
                        "id", "fallback-" + id,
                        "approver", "系统",
                        "status", fallbackStatus,
                        "comment", fallbackComment,
                        "createdAt", asString(contractRows.get(0).get("fallbackTime"))
                ));
            }
        }
        return ResponseEntity.ok(Map.of("records", records, "total", records.size()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_APPROVAL_MANAGER') or hasAuthority('contract:approval')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestParam String status, Authentication authentication) {
        String mapped = toContractDbStatus(status);
        int affected = jdbcTemplate.update(
                "UPDATE contracts SET status = ?, updated_time = NOW() WHERE id = ?",
                mapped, id);
        if (affected == 0) {
            operationLogService.log(authentication, "UPDATE_STATUS", "CONTRACT",
                    "更新状态失败：合同不存在 id=" + id, "FAILED", "合同不存在");
            return ResponseEntity.notFound().build();
        }
        operationLogService.log(authentication, "UPDATE_STATUS", "CONTRACT",
                "更新状态成功 id=" + id + ", status=" + mapped, "SUCCESS", null);
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
                       COALESCE(NULLIF(u.real_name, ''), NULLIF(u.username, ''), CONCAT('用户#', c.created_by)) AS createdBy,
                       DATE_FORMAT(CONVERT_TZ(c.created_time, '+00:00', '+08:00'), '%Y-%m-%d %H:%i:%s') AS createdAt,
                       DATE_FORMAT(CONVERT_TZ(c.created_time, '+00:00', '+08:00'), '%Y-%m-%d %H:%i:%s') AS applyTime,
                       c.description AS description,
                       CASE c.status
                           WHEN 'PENDING' THEN 'pending'
                           WHEN 'DRAFT' THEN 'draft'
                           WHEN 'TERMINATED' THEN 'rejected'
                           ELSE 'approved'
                       END AS status
                FROM contracts c
                LEFT JOIN users u ON u.id = c.created_by
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
        } else {
            // 审批页默认只展示已进入审批流的合同，避免草稿合同被误显示为“已通过”
            sql.append(" AND c.status IN ('PENDING','APPROVED','EXECUTING','COMPLETED','TERMINATED')");
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
        } else {
            countSql.append(" AND c.status IN ('PENDING','APPROVED','EXECUTING','COMPLETED','TERMINATED')");
        }
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total == null ? 0 : total);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/approval-records")
    public ResponseEntity<?> getApprovalRecords(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;

        StringBuilder sql = new StringBuilder("""
                SELECT ar.id,
                       ar.contract_id AS contractId,
                       COALESCE(c.contract_no, '') AS contractNo,
                       COALESCE(c.contract_name, '') AS contractName,
                       COALESCE(u.real_name, u.username, '系统') AS approver,
                       CASE ar.approval_result
                           WHEN 0 THEN 'pending'
                           WHEN 1 THEN 'approved'
                           WHEN 2 THEN 'rejected'
                           ELSE 'pending'
                       END AS status,
                       COALESCE(ar.approval_opinion, '') AS comment,
                       DATE_FORMAT(CONVERT_TZ(ar.approval_time, '+00:00', '+08:00'), '%Y-%m-%d %H:%i:%s') AS approvalTime
                FROM approval_records ar
                LEFT JOIN contracts c ON c.id = ar.contract_id
                LEFT JOIN users u ON u.id = ar.approver_id
                WHERE 1=1
                """);
        StringBuilder countSql = new StringBuilder("""
                SELECT COUNT(*)
                FROM approval_records ar
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        List<Object> countParams = new ArrayList<>();

        Integer resultCode = toApprovalResultCode(status);
        if (resultCode != null) {
            sql.append(" AND ar.approval_result = ?");
            countSql.append(" AND ar.approval_result = ?");
            params.add(resultCode);
            countParams.add(resultCode);
        }

        sql.append(" ORDER BY ar.approval_time DESC, ar.id DESC LIMIT ? OFFSET ?");
        params.add(safeSize);
        params.add(offset);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        Long total = jdbcTemplate.queryForObject(countSql.toString(), Long.class, countParams.toArray());
        return ResponseEntity.ok(Map.of(
                "records", records,
                "total", total == null ? 0 : total,
                "page", safePage,
                "size", safeSize
        ));
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
                       SUM(CASE WHEN CONVERT_TZ(created_time, '+00:00', '+08:00') >= DATE_FORMAT(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00'), '%Y-%m-01')
                                 AND CONVERT_TZ(created_time, '+00:00', '+08:00') < DATE_ADD(LAST_DAY(CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00')), INTERVAL 1 DAY)
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
        BigDecimal totalSalesRevenue = asBigDecimal(row.get("salesRevenue"));

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

        StringBuilder topCustomerSql = new StringBuilder("""
                SELECT COALESCE(NULLIF(TRIM(party_a), ''), '未命名客户') AS customerName,
                       SUM(COALESCE(amount, 0)) AS revenue,
                       COUNT(*) AS contractCount
                FROM contracts
                WHERE contract_type = 'SALES'
                """);
        List<Object> topCustomerParams = new ArrayList<>();
        if (year != null) {
            topCustomerSql.append(" AND signing_year = ?");
            topCustomerParams.add(year);
        }
        topCustomerSql.append("""
                
                GROUP BY COALESCE(NULLIF(TRIM(party_a), ''), '未命名客户')
                ORDER BY revenue DESC, customerName ASC
                LIMIT 20
                """);
        List<Map<String, Object>> topCustomerRows = jdbcTemplate.queryForList(topCustomerSql.toString(), topCustomerParams.toArray());

        List<Map<String, Object>> topCustomerRevenue = new ArrayList<>();
        BigDecimal top5Revenue = BigDecimal.ZERO;
        for (int i = 0; i < topCustomerRows.size(); i++) {
            Map<String, Object> item = topCustomerRows.get(i);
            String customerName = asString(item.get("customerName"));
            BigDecimal revenue = asBigDecimal(item.get("revenue"));
            int contractCount = asInt(item.get("contractCount"));
            if (i < 5) {
                top5Revenue = top5Revenue.add(revenue);
            }
            topCustomerRevenue.add(Map.of(
                    "rank", i + 1,
                    "customerName", defaultIfBlank(customerName, "未命名客户"),
                    "revenue", revenue,
                    "contractCount", contractCount
            ));
        }
        BigDecimal top5Share = BigDecimal.ZERO;
        if (totalSalesRevenue.compareTo(BigDecimal.ZERO) > 0) {
            top5Share = top5Revenue
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalSalesRevenue, 2, java.math.RoundingMode.HALF_UP);
        }
        result.put("topCustomerRevenue", topCustomerRevenue);
        result.put("top5CustomerRevenueShare", top5Share);
        result.put("top5CustomerRevenue", top5Revenue);
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
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAnyAuthority('CONTRACT_TYPE_MANAGE','contract:write')")
    public ResponseEntity<?> createContractType(@RequestBody Map<String, Object> request, Authentication authentication) {
        ensureContractTypeMetaTable();
        String code = normalizeContractTypeCode(asString(request.get("code")));
        String name = normalizeContractTypeName(asString(request.get("name")), code);
        if (!isValidContractTypeCode(code)) {
            operationLogService.log(authentication, "CREATE_TYPE", "CONTRACT",
                    "新增合同类型失败：编码不合法 " + code, "FAILED", "类型编码格式不正确");
            return ResponseEntity.badRequest().body(Map.of("message", "类型编码仅支持2-50位大写字母、数字、下划线"));
        }

        List<String> enumCodes = getContractTypeEnumCodes();
        if (enumCodes.contains(code)) {
            operationLogService.log(authentication, "CREATE_TYPE", "CONTRACT",
                    "新增合同类型失败：编码已存在 " + code, "FAILED", "合同类型编码已存在");
            return ResponseEntity.badRequest().body(Map.of("message", "合同类型编码已存在"));
        }

        List<String> updatedCodes = new ArrayList<>(enumCodes);
        updatedCodes.add(code);
        updateContractTypeEnum(updatedCodes);
        upsertContractTypeMeta(code, name);
        operationLogService.log(authentication, "CREATE_TYPE", "CONTRACT",
                "新增合同类型成功 code=" + code + ", name=" + name, "SUCCESS", null);
        return ResponseEntity.ok(Map.of("code", code, "name", name));
    }

    @PutMapping("/types/{code}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAnyAuthority('CONTRACT_TYPE_MANAGE','contract:write')")
    public ResponseEntity<?> updateContractType(@PathVariable String code,
                                                @RequestBody Map<String, Object> request,
                                                Authentication authentication) {
        ensureContractTypeMetaTable();
        String oldCode = normalizeContractTypeCode(code);
        String newCode = normalizeContractTypeCode(asString(request.get("code")));
        if (isBlank(newCode)) {
            newCode = oldCode;
        }
        String newName = normalizeContractTypeName(asString(request.get("name")), newCode);

        if (!isValidContractTypeCode(oldCode) || !isValidContractTypeCode(newCode)) {
            operationLogService.log(authentication, "UPDATE_TYPE", "CONTRACT",
                    "更新合同类型失败：编码不合法 old=" + oldCode + ", new=" + newCode, "FAILED", "类型编码格式不正确");
            return ResponseEntity.badRequest().body(Map.of("message", "类型编码仅支持2-50位大写字母、数字、下划线"));
        }

        List<String> enumCodes = getContractTypeEnumCodes();
        if (!enumCodes.contains(oldCode)) {
            operationLogService.log(authentication, "UPDATE_TYPE", "CONTRACT",
                    "更新合同类型失败：类型不存在 code=" + oldCode, "FAILED", "合同类型不存在");
            return ResponseEntity.badRequest().body(Map.of("message", "合同类型不存在"));
        }

        if (!oldCode.equals(newCode) && enumCodes.contains(newCode)) {
            operationLogService.log(authentication, "UPDATE_TYPE", "CONTRACT",
                    "更新合同类型失败：新编码已存在 newCode=" + newCode, "FAILED", "新的合同类型编码已存在");
            return ResponseEntity.badRequest().body(Map.of("message", "新的合同类型编码已存在"));
        }

        try {
            if (!oldCode.equals(newCode)) {
                boolean enumColumn = isContractTypeEnumColumn();
                if (enumColumn) {
                    // 先放行新编码，避免旧编码数据在迁移前触发 ENUM 约束错误
                    List<String> expandedCodes = new ArrayList<>(enumCodes);
                    expandedCodes.add(newCode);
                    updateContractTypeEnum(expandedCodes);
                }

                jdbcTemplate.update("UPDATE contracts SET contract_type = ? WHERE contract_type = ?", newCode, oldCode);

                if (enumColumn) {
                    // 数据迁移后再移除旧编码，保持 ENUM 列定义与实际类型一致
                    List<String> finalCodes = new ArrayList<>();
                    for (String item : enumCodes) {
                        finalCodes.add(item.equals(oldCode) ? newCode : item);
                    }
                    updateContractTypeEnum(finalCodes);
                }

                jdbcTemplate.update("DELETE FROM contract_type_meta WHERE type_code = ?", oldCode);
            }
        } catch (IllegalArgumentException | UncategorizedSQLException ex) {
            operationLogService.log(authentication, "UPDATE_TYPE", "CONTRACT",
                    "更新合同类型失败 oldCode=" + oldCode + ", newCode=" + newCode,
                    "FAILED", ex.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", "更新合同类型失败，请检查编码后重试"));
        }

        upsertContractTypeMeta(newCode, newName);
        operationLogService.log(authentication, "UPDATE_TYPE", "CONTRACT",
                "更新合同类型成功 oldCode=" + oldCode + ", newCode=" + newCode, "SUCCESS", null);
        return ResponseEntity.ok(Map.of("code", newCode, "name", newName));
    }

    @DeleteMapping("/types/{code}")
    @PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN','ROLE_ADMIN','ROLE_ROLE_ADMIN','ROLE_CONTRACT_MANAGER') or hasAnyAuthority('CONTRACT_TYPE_MANAGE','contract:write')")
    public ResponseEntity<?> deleteContractType(@PathVariable String code, Authentication authentication) {
        ensureContractTypeMetaTable();
        String typeCode = normalizeContractTypeCode(code);
        if (!isValidContractTypeCode(typeCode)) {
            operationLogService.log(authentication, "DELETE_TYPE", "CONTRACT",
                    "删除合同类型失败：编码不合法 code=" + typeCode, "FAILED", "合同类型编码格式不正确");
            return ResponseEntity.badRequest().body(Map.of("message", "合同类型编码格式不正确"));
        }

        List<String> enumCodes = getContractTypeEnumCodes();
        if (!enumCodes.contains(typeCode)) {
            operationLogService.log(authentication, "DELETE_TYPE", "CONTRACT",
                    "删除合同类型失败：类型不存在 code=" + typeCode, "FAILED", "合同类型不存在");
            return ResponseEntity.badRequest().body(Map.of("message", "合同类型不存在"));
        }

        Long usedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contracts WHERE contract_type = ?", Long.class, typeCode);
        if (usedCount != null && usedCount > 0) {
            operationLogService.log(authentication, "DELETE_TYPE", "CONTRACT",
                    "删除合同类型失败：类型被使用 code=" + typeCode, "FAILED", "该类型已被合同使用，无法删除");
            return ResponseEntity.badRequest().body(Map.of("message", "该类型已被合同使用，无法删除"));
        }

        List<String> updatedCodes = new ArrayList<>();
        for (String item : enumCodes) {
            if (!item.equals(typeCode)) {
                updatedCodes.add(item);
            }
        }
        if (updatedCodes.isEmpty()) {
            operationLogService.log(authentication, "DELETE_TYPE", "CONTRACT",
                    "删除合同类型失败：至少保留一个类型", "FAILED", "至少保留一个合同类型");
            return ResponseEntity.badRequest().body(Map.of("message", "至少保留一个合同类型"));
        }
        updateContractTypeEnum(updatedCodes);
        jdbcTemplate.update("DELETE FROM contract_type_meta WHERE type_code = ?", typeCode);
        operationLogService.log(authentication, "DELETE_TYPE", "CONTRACT",
                "删除合同类型成功 code=" + typeCode, "SUCCESS", null);
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

    private static boolean isApprovedOrRejectedStatus(String status) {
        if (isBlank(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "APPROVED".equals(normalized)
                || "EXECUTING".equals(normalized)
                || "COMPLETED".equals(normalized)
                || "TERMINATED".equals(normalized);
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || isBlank(authentication.getName())) {
            return 1L;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE username = ? LIMIT 1",
                    Long.class,
                    authentication.getName()
            );
        } catch (Exception e) {
            return 1L;
        }
    }

    private static boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(
                authority -> "ROLE_SUPER_ADMIN".equalsIgnoreCase(authority.getAuthority())
                        || "ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority())
                        || "ROLE_ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority()));
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

    private static Integer toApprovalResultCode(String status) {
        if (isBlank(status)) {
            return null;
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "pending" -> 0;
            case "approved" -> 1;
            case "rejected" -> 2;
            default -> null;
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

    private void ensureContractParticipantTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS contract_participant_entries (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    contract_id BIGINT NOT NULL,
                    participant_name VARCHAR(100) NOT NULL,
                    participant_role VARCHAR(100),
                    department VARCHAR(100),
                    phone VARCHAR(50),
                    sort_order INT DEFAULT 0,
                    created_by BIGINT,
                    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_contract_id (contract_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """);
    }

    private void saveParticipants(Long contractId, Object rawParticipants, Long operatorId) {
        ensureContractParticipantTable();
        jdbcTemplate.update("DELETE FROM contract_participant_entries WHERE contract_id = ?", contractId);
        List<Map<String, Object>> participants = asMapList(rawParticipants);
        int sortOrder = 0;
        for (Map<String, Object> participant : participants) {
            String name = nullable(asString(participant.get("name")));
            String role = nullable(asString(participant.get("role")));
            String department = nullable(asString(participant.get("department")));
            String phone = nullable(asString(participant.get("phone")));
            if (name == null && role == null && department == null && phone == null) {
                continue;
            }
            jdbcTemplate.update("""
                            INSERT INTO contract_participant_entries (
                                contract_id, participant_name, participant_role, department, phone, sort_order, created_by
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    contractId,
                    defaultIfBlank(name, "未命名参与人员"),
                    role,
                    department,
                    phone,
                    sortOrder++,
                    operatorId);
        }
    }

    private List<Map<String, Object>> queryParticipantRecords(Long contractId) {
        ensureContractParticipantTable();
        return jdbcTemplate.queryForList("""
                SELECT id,
                       participant_name AS name,
                       COALESCE(participant_role, '') AS role,
                       COALESCE(department, '') AS department,
                       COALESCE(phone, '') AS phone
                FROM contract_participant_entries
                WHERE contract_id = ?
                ORDER BY sort_order ASC, id ASC
                """, contractId);
    }

    private List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        normalized.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                result.add(normalized);
            }
        }
        return result;
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
                           DATE_FORMAT(CONVERT_TZ(created_at, '+00:00', '+08:00'), '%Y-%m-%d %H:%i:%s') AS uploadTime
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
                       DATE_FORMAT(CONVERT_TZ(created_time, '+00:00', '+08:00'), '%Y-%m-%d %H:%i:%s') AS uploadTime
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
            LinkedHashSet<String> values = new LinkedHashSet<>(List.of("SALES", "PURCHASE", "SERVICE", "OTHER"));

            List<Map<String, Object>> metaRows = jdbcTemplate.queryForList(
                    "SELECT type_code FROM contract_type_meta");
            for (Map<String, Object> row : metaRows) {
                String code = normalizeContractTypeCode(asString(row.get("type_code")));
                if (!isBlank(code)) {
                    values.add(code);
                }
            }

            List<Map<String, Object>> contractRows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT contract_type FROM contracts WHERE contract_type IS NOT NULL AND contract_type <> ''");
            for (Map<String, Object> row : contractRows) {
                String code = normalizeContractTypeCode(asString(row.get("contract_type")));
                if (!isBlank(code)) {
                    values.add(code);
                }
            }

            return new ArrayList<>(values);
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
        if (!isContractTypeEnumColumn()) {
            return;
        }
        StringBuilder sql = new StringBuilder("ALTER TABLE contracts MODIFY COLUMN contract_type ENUM(");
        for (int i = 0; i < typeCodes.size(); i++) {
            String code = normalizeContractTypeCode(typeCodes.get(i));
            if (!isValidContractTypeCode(code)) {
                throw new IllegalArgumentException("检测到非法合同类型编码，拒绝执行DDL: " + typeCodes.get(i));
            }
            if (i > 0) {
                sql.append(",");
            }
            sql.append("'").append(escapeSqlLiteral(code)).append("'");
        }
        sql.append(") NOT NULL");
        jdbcTemplate.execute(sql.toString());
    }

    private boolean isContractTypeEnumColumn() {
        String columnType = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'contracts'
                  AND COLUMN_NAME = 'contract_type'
                LIMIT 1
                """, String.class);
        return columnType != null && columnType.toLowerCase(Locale.ROOT).startsWith("enum(");
    }

    private void upsertContractTypeMeta(String code, String name) {
        jdbcTemplate.update("""
                        INSERT INTO contract_type_meta (type_code, type_name)
                        VALUES (?, ?)
                        ON DUPLICATE KEY UPDATE type_name = VALUES(type_name)
                        """,
                code, name);
    }

    private void ensureContractTypeAvailable(String rawCode) {
        String code = normalizeContractTypeCode(rawCode);
        if (!isValidContractTypeCode(code)) {
            return;
        }
        ensureContractTypeMetaTable();
        List<String> enumCodes = getContractTypeEnumCodes();
        if (!enumCodes.contains(code)) {
            List<String> updatedCodes = new ArrayList<>(enumCodes);
            updatedCodes.add(code);
            updateContractTypeEnum(updatedCodes);
        }
        String existingName = jdbcTemplate.query("""
                        SELECT type_name
                        FROM contract_type_meta
                        WHERE type_code = ?
                        LIMIT 1
                        """,
                rs -> rs.next() ? rs.getString("type_name") : null,
                code);
        if (isBlank(existingName)) {
            upsertContractTypeMeta(code, defaultContractTypeName(code));
        }
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

    private static String escapeSqlLiteral(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("'", "''");
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
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        for (DateTimeFormatter formatter : FLEXIBLE_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(raw, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
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

    private static String firstNonBlank(String primary, String fallback) {
        if (!isBlank(primary)) {
            return primary;
        }
        return fallback;
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
                                                              String customerName,
                                                              String contractType,
                                                              String signingYears,
                                                              String status,
                                                              String startDate,
                                                              String endDate) {
        ensureContractTypeMetaTable();
        StringBuilder sql = new StringBuilder("""
                SELECT c.id,
                       c.id AS contractId,
                       c.contract_no AS contractNo,
                       c.contract_no AS contractNumber,
                       c.signing_year AS signingYear,
                       c.contract_name AS contractName,
                       COALESCE(NULLIF(TRIM(c.party_a), ''), '') AS customerName,
                       COALESCE(NULLIF(TRIM(c.party_b), ''), '') AS companySignatory,
                       c.contract_type AS contractType,
                       COALESCE(
                           NULLIF(TRIM(ctm.type_name), ''),
                           CASE c.contract_type
                               WHEN 'SALES' THEN '销售合同'
                               WHEN 'PURCHASE' THEN '采购合同'
                               WHEN 'SERVICE' THEN '服务合同'
                               WHEN 'OTHER' THEN '其他'
                               ELSE c.contract_type
                           END
                       ) AS contractTypeLabel,
                       c.amount AS amount,
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
                LEFT JOIN contract_type_meta ctm ON ctm.type_code = c.contract_type
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (!isBlank(keyword)) {
            sql.append(" AND (c.contract_no LIKE ? OR c.contract_name LIKE ? OR c.party_a LIKE ?)");
            String likeKeyword = "%" + keyword.trim() + "%";
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
        }
        if (!isBlank(customerName)) {
            sql.append(" AND c.party_a LIKE ?");
            params.add("%" + customerName.trim() + "%");
        }
        if (!isBlank(contractType)) {
            sql.append(" AND c.contract_type = ?");
            params.add(contractType.trim().toUpperCase(Locale.ROOT));
        }
        List<Integer> signingYearList = parseSigningYears(signingYears);
        if (!signingYearList.isEmpty()) {
            sql.append(" AND c.signing_year IN (");
            appendPlaceholders(sql, signingYearList.size());
            sql.append(")");
            params.addAll(signingYearList);
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
