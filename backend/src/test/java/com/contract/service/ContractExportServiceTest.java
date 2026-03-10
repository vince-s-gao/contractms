package com.contract.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractExportServiceTest {

    private final ContractExportService contractExportService = new ContractExportService();

    @Test
    void shouldExportSelectedFieldsToExcel() throws Exception {
        List<Map<String, Object>> records = List.of(
                Map.of(
                        "contractNo", "HT20260001",
                        "contractName", "测试导出合同",
                        "amount", new BigDecimal("500000"),
                        "status", "active"
                )
        );

        byte[] bytes = contractExportService.exportToExcel(
                records,
                List.of("contractNo", "contractName", "amount", "status"));

        assertTrue(bytes.length > 0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertEquals("合同编号", header.getCell(0).getStringCellValue());
            assertEquals("合同名称", header.getCell(1).getStringCellValue());
            assertEquals("合同金额", header.getCell(2).getStringCellValue());
            assertEquals("状态", header.getCell(3).getStringCellValue());

            Row dataRow = workbook.getSheetAt(0).getRow(1);
            assertEquals("HT20260001", dataRow.getCell(0).getStringCellValue());
            assertEquals("测试导出合同", dataRow.getCell(1).getStringCellValue());
            assertEquals(500000d, dataRow.getCell(2).getNumericCellValue());
            assertEquals("已生效", dataRow.getCell(3).getStringCellValue());
        }
    }
}
