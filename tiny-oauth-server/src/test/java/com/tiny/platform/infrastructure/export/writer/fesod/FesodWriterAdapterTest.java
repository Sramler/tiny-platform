package com.tiny.platform.infrastructure.export.writer.fesod;

import com.tiny.platform.infrastructure.export.core.AggregateStrategy;
import com.tiny.platform.infrastructure.export.service.SheetWriteModel;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FesodWriterAdapterTest {

    @Test
    void shouldSplitSheetAndKeepSummaryOnLastPart() throws Exception {
        FesodWriterAdapter adapter = new FesodWriterAdapter(2, 5);
        SheetWriteModel model = new SheetWriteModel();
        model.setSheetName("demo_export_usage");
        model.setHead(List.of(
            List.of("ID"),
            List.of("Amount")
        ));
        model.setLeafFields(List.of("id", "amount"));
        model.setRows(buildAmountRows(8).iterator());
        model.setStrategy(new SumAmountStrategy());
        Map<String, Object> sumMap = new HashMap<>();
        sumMap.put("id", null);
        sumMap.put("amount", 36L);
        model.setSumMap(sumMap);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        adapter.writeMultiSheet(out, List.of(model));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertEquals(3, wb.getNumberOfSheets());
            assertEquals("demo_export_usage", wb.getSheetName(0));
            assertEquals("demo_export_usage_2", wb.getSheetName(1));
            assertEquals("demo_export_usage_3", wb.getSheetName(2));

            assertEquals(5, wb.getSheetAt(0).getPhysicalNumberOfRows());
            assertEquals(5, wb.getSheetAt(1).getPhysicalNumberOfRows());
            assertEquals(2, wb.getSheetAt(2).getPhysicalNumberOfRows());
            assertEquals("总计", wb.getSheetAt(2).getRow(1).getCell(0).getStringCellValue());
            assertEquals(36D, wb.getSheetAt(2).getRow(1).getCell(1).getNumericCellValue());
        }
    }

    @Test
    void shouldRepeatTopInfoAndHeaderOnSplitSheets() throws Exception {
        FesodWriterAdapter adapter = new FesodWriterAdapter(2, 5);
        SheetWriteModel model = new SheetWriteModel();
        model.setSheetName("usage");
        model.setTopInfoRows(List.of(
            List.of("租户A"),
            List.of("导出人")
        ));
        model.setHead(List.of(
            List.of("ID"),
            List.of("Product")
        ));
        model.setRows(buildRows(4).iterator());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        adapter.writeMultiSheet(out, List.of(model));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertSheetHeader(wb.getSheetAt(0));
            assertSheetHeader(wb.getSheetAt(1));
        }
    }

    private void assertSheetHeader(Sheet sheet) {
        assertEquals("租户A", sheet.getRow(0).getCell(0).getStringCellValue());
        assertEquals("导出人", sheet.getRow(0).getCell(1).getStringCellValue());
        assertEquals("ID", sheet.getRow(1).getCell(0).getStringCellValue());
        assertEquals("Product", sheet.getRow(1).getCell(1).getStringCellValue());
    }

    private List<List<Object>> buildRows(int count) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(List.of((long) i, "v-" + i));
        }
        return rows;
    }

    private List<List<Object>> buildAmountRows(int count) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(List.of((long) i, (long) i));
        }
        return rows;
    }

    private static final class SumAmountStrategy implements AggregateStrategy {
        @Override
        public boolean isAggregate(String fieldName) {
            return "amount".equals(fieldName);
        }

        @Override
        public Object accumulate(String fieldName, Object currentValue, Object accumulatedValue) {
            return null;
        }

        @Override
        public Object finalize(String fieldName, Object accumulatedValue) {
            return accumulatedValue;
        }
    }
}
