package com.tiny.platform.infrastructure.export.writer.poi;

import com.tiny.platform.infrastructure.export.service.SheetWriteModel;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class POIWriterAdapterTest {

    @Test
    void shouldSplitSheetWhenDataRowsExceedLimit() throws Exception {
        POIWriterAdapter adapter = new POIWriterAdapter(10, 5);
        SheetWriteModel model = new SheetWriteModel();
        model.setSheetName("demo_export_usage");
        model.setRows(buildRows(12).iterator());

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
        }
    }

    @Test
    void shouldRepeatHeaderRowsOnSplitSheets() throws Exception {
        POIWriterAdapter adapter = new POIWriterAdapter(10, 5);
        SheetWriteModel model = new SheetWriteModel();
        model.setSheetName("usage");
        model.setHead(List.of(
                List.of("ID"),
                List.of("Product")
        ));
        model.setRows(buildRows(6).iterator());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        adapter.writeMultiSheet(out, List.of(model));

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertEquals("usage", wb.getSheetName(0));
            assertEquals("usage_2", wb.getSheetName(1));

            Sheet first = wb.getSheetAt(0);
            Sheet second = wb.getSheetAt(1);
            assertEquals("ID", first.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Product", first.getRow(0).getCell(1).getStringCellValue());
            assertEquals("ID", second.getRow(0).getCell(0).getStringCellValue());
            assertEquals("Product", second.getRow(0).getCell(1).getStringCellValue());
            assertEquals(5, first.getPhysicalNumberOfRows());
            assertEquals(3, second.getPhysicalNumberOfRows());
        }
    }

    private List<List<Object>> buildRows(int count) {
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(List.of((long) i, "v-" + i));
        }
        return rows;
    }
}
