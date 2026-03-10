package com.contract.service;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ContractImportServiceTest {

    private final ContractImportService contractImportService = new ContractImportService();

    @Test
    void shouldParseRowsFromExcel() throws Exception {
        byte[] excelBytes = buildExcel();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contracts.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        List<ContractImportService.ImportRow> rows = contractImportService.parseContractRows(file);

        assertEquals(1, rows.size());
        ContractImportService.ImportRow row = rows.get(0);
        assertEquals(2, row.rowNumber());
        assertEquals("HT20260088", row.values().get("contractNo"));
        assertEquals("批量导入测试合同", row.values().get("contractName"));
        assertEquals("服务", row.values().get("contractType"));
        assertEquals(new BigDecimal("12345.67"), row.values().get("amount"));
        assertEquals("2026-03-01", row.values().get("startDate"));
        assertEquals("2026-12-31", row.values().get("endDate"));
        assertFalse(row.values().isEmpty());
    }

    @Test
    void shouldFallbackToPositionalMappingWhenHeaderMissing() throws Exception {
        byte[] excelBytes = buildExcelWithoutStandardHeaders();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contracts-no-header.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                excelBytes
        );

        List<ContractImportService.ImportRow> rows = contractImportService.parseContractRows(file);
        assertEquals(1, rows.size());
        ContractImportService.ImportRow row = rows.get(0);
        assertEquals("HTX-001", row.values().get("contractNo"));
        assertEquals("无标准表头合同", row.values().get("contractName"));
    }

    private byte[] buildExcel() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("合同");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("合同编号");
            header.createCell(1).setCellValue("合同名称");
            header.createCell(2).setCellValue("合同类型");
            header.createCell(3).setCellValue("合同金额");
            header.createCell(4).setCellValue("开始日期");
            header.createCell(5).setCellValue("结束日期");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("HT20260088");
            row.createCell(1).setCellValue("批量导入测试合同");
            row.createCell(2).setCellValue("服务");
            row.createCell(3).setCellValue(12345.67);
            row.createCell(4).setCellValue("2026-03-01");
            row.createCell(5).setCellValue("2026-12-31");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] buildExcelWithoutStandardHeaders() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("合同");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("编号");
            header.createCell(1).setCellValue("名称");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("HTX-001");
            row.createCell(1).setCellValue("无标准表头合同");

            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
