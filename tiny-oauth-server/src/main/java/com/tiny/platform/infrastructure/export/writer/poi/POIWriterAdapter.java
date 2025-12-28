package com.tiny.platform.infrastructure.export.writer.poi;

import com.tiny.platform.infrastructure.export.service.SheetWriteModel;
import com.tiny.platform.infrastructure.export.writer.WriterAdapter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * POIWriterAdapter 使用 SXSSFWorkbook 流式写入
 */
public class POIWriterAdapter implements WriterAdapter {

    private final int rowAccessWindowSize;

    public POIWriterAdapter() { this(200); }
    public POIWriterAdapter(int rowAccessWindowSize) { this.rowAccessWindowSize = rowAccessWindowSize; }

    @Override
    public void writeMultiSheet(OutputStream out, List<SheetWriteModel> sheets) throws Exception {
        SXSSFWorkbook workbook = new SXSSFWorkbook(rowAccessWindowSize);
        try {
            int sheetIndex = 0;
            for (SheetWriteModel model : sheets) {
                String sheetName = model.getSheetName() == null ? "Sheet" + (++sheetIndex) : model.getSheetName();
                Sheet sheet = workbook.createSheet(sheetName);
                List<List<String>> head = model.getHead();
                List<List<String>> topInfoRows = model.getTopInfoRows();

                int topRowCount = 0;
                if (topInfoRows != null && !topInfoRows.isEmpty()) {
                    // 写整体 topInfo（简单实现：逐行写）
                    for (int r = 0; r < topInfoRows.size(); r++) {
                        Row tr = sheet.createRow(r);
                        List<String> cols = topInfoRows.get(r);
                        for (int c = 0; c < cols.size(); c++) tr.createCell(c).setCellValue(cols.get(c));
                    }
                    topRowCount = topInfoRows.size();
                }

                if (head != null && !head.isEmpty()) {
                    int headRows = head.get(0).size();
                    for (int r = 0; r < headRows; r++) {
                        Row hr = sheet.createRow(topRowCount + r);
                        for (int c = 0; c < head.size(); c++) {
                            hr.createCell(c).setCellValue(head.get(c).get(r));
                        }
                    }
                    mergeHeaderParents(sheet, topRowCount, head);
                }

                // 数据写入
                Iterator<List<Object>> rows = model.getRows();
                int rowIndex = topRowCount + (head == null || head.isEmpty() ? 0 : head.get(0).size());
                Map<String, Object> sumMap = model.getSumMap();
                List<String> leafFields = model.getLeafFields();

                while (rows != null && rows.hasNext()) {
                    List<Object> rowData = rows.next();
                    Row row = sheet.createRow(rowIndex++);
                    for (int c = 0; c < rowData.size(); c++) {
                        Object v = rowData.get(c);
                        Cell cell = row.createCell(c);
                        if (v == null) continue;
                        if (v instanceof Number) cell.setCellValue(((Number) v).doubleValue());
                        else if (v instanceof Boolean) cell.setCellValue((Boolean) v);
                        else cell.setCellValue(v.toString());
                    }
                }

                // 写合计（若有）
                if (model.getStrategy() != null && sumMap != null) {
                    Row sumRow = sheet.createRow(rowIndex++);
                    for (int c = 0; c < (leafFields == null ? 0 : leafFields.size()); c++) {
                        Cell cell = sumRow.createCell(c);
                        String f = leafFields.get(c);
                        if (model.getStrategy().isAggregate(f)) {
                            Object outVal = model.getStrategy().finalize(f, sumMap.get(f));
                            if (outVal instanceof Number) cell.setCellValue(((Number) outVal).doubleValue());
                            else if (outVal != null) cell.setCellValue(outVal.toString());
                        } else if (c == 0) {
                            cell.setCellValue("总计");
                        }
                    }
                }
            }

            workbook.write(out);
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private void mergeHeaderParents(Sheet sheet, int startRow, List<List<String>> head) {
        if (head == null || head.isEmpty()) return;
        int levels = head.get(0).size();
        for (int level = 0; level < levels; level++) {
            int col = 0;
            while (col < head.size()) {
                String value = head.get(col).get(level);
                if (value == null || value.isEmpty()) { col++; continue; }
                int start = col;
                int end = col;
                col++;
                while (col < head.size() && value.equals(head.get(col).get(level))) {
                    end++;
                    col++;
                }
                if (end > start) sheet.addMergedRegion(new CellRangeAddress(startRow + level, startRow + level, start, end));
            }
        }
    }
}