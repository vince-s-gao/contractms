package com.contract.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ContractExportService {

    private static final LinkedHashMap<String, String> FIELD_LABELS = new LinkedHashMap<>();

    static {
        FIELD_LABELS.put("contractNo", "合同编号");
        FIELD_LABELS.put("signingYear", "签约年份");
        FIELD_LABELS.put("contractName", "合同名称");
        FIELD_LABELS.put("customerName", "客户名称");
        FIELD_LABELS.put("companySignatory", "公司签约主体");
        FIELD_LABELS.put("contractType", "合同类型");
        FIELD_LABELS.put("amount", "合同金额");
        FIELD_LABELS.put("status", "状态");
        FIELD_LABELS.put("startDate", "开始日期");
        FIELD_LABELS.put("endDate", "结束日期");
        FIELD_LABELS.put("createdBy", "创建人");
        FIELD_LABELS.put("createdAt", "创建时间");
    }

    public byte[] exportToExcel(List<Map<String, Object>> records, List<String> selectedFields) {
        List<String> fields = normalizeFields(selectedFields);
        List<Map<String, Object>> safeRecords = records == null ? Collections.emptyList() : records;

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("合同列表");

            Row header = sheet.createRow(0);
            for (int i = 0; i < fields.size(); i++) {
                String field = fields.get(i);
                Cell cell = header.createCell(i);
                cell.setCellValue(FIELD_LABELS.get(field));
            }

            for (int rowIndex = 0; rowIndex < safeRecords.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                Map<String, Object> record = safeRecords.get(rowIndex);
                for (int col = 0; col < fields.size(); col++) {
                    String field = fields.get(col);
                    Cell cell = row.createCell(col);
                    writeCellValue(cell, field, record.get(field));
                }
            }

            for (int i = 0; i < fields.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("导出Excel失败", e);
        }
    }

    private static List<String> normalizeFields(List<String> selectedFields) {
        if (selectedFields == null || selectedFields.isEmpty()) {
            return new ArrayList<>(FIELD_LABELS.keySet());
        }
        List<String> validFields = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String field : selectedFields) {
            if (field != null && FIELD_LABELS.containsKey(field) && seen.add(field)) {
                validFields.add(field);
            }
        }
        if (validFields.isEmpty()) {
            return new ArrayList<>(FIELD_LABELS.keySet());
        }
        return validFields;
    }

    private static void writeCellValue(Cell cell, String field, Object value) {
        if ("amount".equals(field)) {
            BigDecimal amount = toBigDecimal(value);
            if (amount != null) {
                cell.setCellValue(amount.doubleValue());
                return;
            }
        }
        if ("status".equals(field)) {
            cell.setCellValue(toStatusText(value));
            return;
        }
        if ("contractType".equals(field)) {
            cell.setCellValue(toContractTypeText(value));
            return;
        }
        cell.setCellValue(value == null ? "" : value.toString());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String toStatusText(Object value) {
        if (value == null) {
            return "";
        }
        return switch (value.toString().toLowerCase(Locale.ROOT)) {
            case "draft" -> "草稿";
            case "approving", "pending" -> "审批中";
            case "active", "approved" -> "已生效";
            case "terminated", "rejected" -> "已终止";
            default -> value.toString();
        };
    }

    private static String toContractTypeText(Object value) {
        if (value == null) {
            return "";
        }
        String type = value.toString().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "SALES" -> "销售";
            case "PURCHASE" -> "采购";
            case "SERVICE" -> "服务";
            case "OTHER" -> "其他";
            default -> value.toString();
        };
    }
}
