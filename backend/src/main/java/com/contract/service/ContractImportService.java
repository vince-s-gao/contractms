package com.contract.service;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class ContractImportService {

    private static final Map<String, String> HEADER_TO_FIELD = new LinkedHashMap<>();
    private static final List<String> POSITIONAL_FIELDS = List.of(
            "contractNo",
            "contractName",
            "customerName",
            "companySignatory",
            "contractType",
            "amount",
            "status",
            "startDate",
            "endDate",
            "createdBy"
    );
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy/M/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd")
    );

    static {
        HEADER_TO_FIELD.put("合同编号", "contractNo");
        HEADER_TO_FIELD.put("合同名称", "contractName");
        HEADER_TO_FIELD.put("客户名称", "customerName");
        HEADER_TO_FIELD.put("公司签约主体", "companySignatory");
        HEADER_TO_FIELD.put("合同类型", "contractType");
        HEADER_TO_FIELD.put("合同金额", "amount");
        HEADER_TO_FIELD.put("状态", "status");
        HEADER_TO_FIELD.put("开始日期", "startDate");
        HEADER_TO_FIELD.put("结束日期", "endDate");
        HEADER_TO_FIELD.put("创建人", "createdBy");
    }

    public List<ImportRow> parseContractRows(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Excel中没有可读取的工作表");
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("Excel表头不能为空");
            }

            Map<Integer, String> fieldByColumn = parseHeader(headerRow);
            if (fieldByColumn.isEmpty()) {
                fieldByColumn = parseHeaderByPosition(headerRow);
            }

            List<ImportRow> rows = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                Map<String, Object> values = new HashMap<>();
                for (Map.Entry<Integer, String> entry : fieldByColumn.entrySet()) {
                    int col = entry.getKey();
                    String field = entry.getValue();
                    Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    Object value = switch (field) {
                        case "amount" -> readNumeric(cell);
                        case "startDate", "endDate" -> readDate(cell);
                        default -> readString(cell);
                    };
                    if (value != null) {
                        values.put(field, value);
                    }
                }

                if (isEffectivelyEmpty(values)) {
                    continue;
                }
                rows.add(new ImportRow(rowIndex + 1, values));
            }
            return rows;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析Excel失败，请检查文件格式是否正确", e);
        }
    }

    private static Map<Integer, String> parseHeader(Row headerRow) {
        Map<Integer, String> result = new HashMap<>();
        short maxCell = headerRow.getLastCellNum();
        for (int i = 0; i < maxCell; i++) {
            Cell cell = headerRow.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            String headerName = readString(cell);
            if (headerName == null) {
                continue;
            }
            String field = HEADER_TO_FIELD.get(headerName.trim());
            if (field != null) {
                result.put(i, field);
            }
        }
        return result;
    }

    private static Map<Integer, String> parseHeaderByPosition(Row headerRow) {
        Map<Integer, String> result = new HashMap<>();
        short maxCell = headerRow.getLastCellNum();
        int maxColumns = Math.max(maxCell, (short) POSITIONAL_FIELDS.size());
        for (int i = 0; i < maxColumns && i < POSITIONAL_FIELDS.size(); i++) {
            result.put(i, POSITIONAL_FIELDS.get(i));
        }
        return result;
    }

    private static String readString(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> emptyToNull(cell.getStringCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    yield date.toString();
                }
                double value = cell.getNumericCellValue();
                if (Math.floor(value) == value) {
                    yield String.valueOf((long) value);
                }
                yield BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> emptyToNull(cell.toString());
            default -> null;
        };
    }

    private static BigDecimal readNumeric(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String text = readString(cell);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private static String readDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            LocalDate date = cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            return date.toString();
        }
        String text = readString(cell);
        if (text == null) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter).toString();
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDate.parse(text).toString();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static boolean isEffectivelyEmpty(Map<String, Object> values) {
        if (values.isEmpty()) {
            return true;
        }
        for (Object value : values.values()) {
            if (value == null) {
                continue;
            }
            if (value instanceof String str && str.isBlank()) {
                continue;
            }
            return false;
        }
        return true;
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ImportRow(int rowNumber, Map<String, Object> values) {
    }
}
