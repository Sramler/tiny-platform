package com.tiny.platform.infrastructure.export.writer.poi;

import com.tiny.platform.infrastructure.export.service.SheetWriteModel;
import com.tiny.platform.infrastructure.export.writer.WriterAdapter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * POIWriterAdapter 使用 SXSSFWorkbook 流式写入
 */
public class POIWriterAdapter implements WriterAdapter {
    private static final Logger log = LoggerFactory.getLogger(POIWriterAdapter.class);

    private static final int XLSX_MAX_ROWS = 1_048_576;
    private static final int SHEET_NAME_MAX_LEN = 31;
    private final int rowAccessWindowSize;
    private final int maxRowsPerSheet;
    private final boolean compressTempFiles;

    public POIWriterAdapter() {
        this(200);
    }

    public POIWriterAdapter(int rowAccessWindowSize) {
        this(rowAccessWindowSize, XLSX_MAX_ROWS);
    }

    public POIWriterAdapter(int rowAccessWindowSize, int maxRowsPerSheet) {
        this(rowAccessWindowSize, maxRowsPerSheet, true);
    }

    public POIWriterAdapter(int rowAccessWindowSize, int maxRowsPerSheet, boolean compressTempFiles) {
        this.rowAccessWindowSize = rowAccessWindowSize;
        this.maxRowsPerSheet = Math.max(1, Math.min(maxRowsPerSheet, XLSX_MAX_ROWS));
        this.compressTempFiles = compressTempFiles;
    }

    @Override
    public void writeMultiSheet(OutputStream out, List<SheetWriteModel> sheets) throws Exception {
        SXSSFWorkbook workbook = new SXSSFWorkbook(rowAccessWindowSize);
        workbook.setCompressTempFiles(compressTempFiles);
        try {
            int sheetIndex = 0;
            Set<String> usedSheetNames = new HashSet<>();
            for (SheetWriteModel model : sheets) {
                String baseSheetName = (model.getSheetName() == null || model.getSheetName().isBlank())
                        ? "Sheet" + (++sheetIndex)
                        : model.getSheetName();
                List<List<String>> head = model.getHead();
                List<List<String>> topInfoRows = model.getTopInfoRows();
                SheetCursor cursor = openSheet(workbook, baseSheetName, 1, topInfoRows, head, usedSheetNames);
                int part = 1;

                // 数据写入
                Iterator<List<Object>> rows = model.getRows();
                Map<String, Object> sumMap = model.getSumMap();
                List<String> leafFields = model.getLeafFields();

                try {
                    while (rows != null && rows.hasNext()) {
                        if (cursor.rowIndex >= maxRowsPerSheet) {
                            cursor = openSheet(workbook, baseSheetName, ++part, topInfoRows, head, usedSheetNames);
                        }
                        List<Object> rowData = rows.next();
                        Row row = cursor.sheet.createRow(cursor.rowIndex++);
                        for (int c = 0; c < rowData.size(); c++) {
                            Object v = rowData.get(c);
                            Cell cell = row.createCell(c);
                            if (v == null) continue;
                            if (v instanceof Number) cell.setCellValue(((Number) v).doubleValue());
                            else if (v instanceof Boolean) cell.setCellValue((Boolean) v);
                            else cell.setCellValue(v.toString());
                        }
                    }
                } finally {
                    closeIterator(rows);
                }

                // 写合计（若有）
                if (model.getStrategy() != null && sumMap != null) {
                    if (cursor.rowIndex >= maxRowsPerSheet) {
                        cursor = openSheet(workbook, baseSheetName, ++part, topInfoRows, head, usedSheetNames);
                    }
                    Row sumRow = cursor.sheet.createRow(cursor.rowIndex++);
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

    private SheetCursor openSheet(SXSSFWorkbook workbook,
                                  String baseSheetName,
                                  int part,
                                  List<List<String>> topInfoRows,
                                  List<List<String>> head,
                                  Set<String> usedSheetNames) {
        String desired = buildSheetName(baseSheetName, part);
        String actual = resolveUniqueSheetName(desired, usedSheetNames);
        Sheet sheet = workbook.createSheet(actual);
        int dataStartRow = writeTopInfoAndHeader(sheet, topInfoRows, head);
        if (dataStartRow >= maxRowsPerSheet) {
            throw new IllegalArgumentException("表头行数超过单Sheet最大行数限制: " + maxRowsPerSheet);
        }
        return new SheetCursor(sheet, dataStartRow);
    }

    private int writeTopInfoAndHeader(Sheet sheet, List<List<String>> topInfoRows, List<List<String>> head) {
        int topRowCount = 0;
        if (topInfoRows != null && !topInfoRows.isEmpty()) {
            for (int r = 0; r < topInfoRows.size(); r++) {
                Row tr = sheet.createRow(r);
                List<String> cols = topInfoRows.get(r);
                for (int c = 0; c < cols.size(); c++) {
                    tr.createCell(c).setCellValue(cols.get(c));
                }
            }
            topRowCount = topInfoRows.size();
        }

        int headRows = 0;
        if (head != null && !head.isEmpty()) {
            headRows = head.get(0).size();
            for (int r = 0; r < headRows; r++) {
                Row hr = sheet.createRow(topRowCount + r);
                for (int c = 0; c < head.size(); c++) {
                    hr.createCell(c).setCellValue(head.get(c).get(r));
                }
            }
            mergeHeaderParents(sheet, topRowCount, head);
        }
        return topRowCount + headRows;
    }

    private String buildSheetName(String baseName, int part) {
        String normalizedBase = baseName == null || baseName.isBlank() ? "Sheet" : baseName.trim();
        if (part <= 1) {
            return truncateSheetName(normalizedBase);
        }
        String suffix = "_" + part;
        int allowedBaseLen = Math.max(1, SHEET_NAME_MAX_LEN - suffix.length());
        String truncatedBase = truncateSheetName(normalizedBase, allowedBaseLen);
        return truncatedBase + suffix;
    }

    private String resolveUniqueSheetName(String desired, Set<String> usedSheetNames) {
        String base = desired == null || desired.isBlank() ? "Sheet" : desired;
        String candidate = base;
        int idx = 1;
        while (usedSheetNames.contains(candidate)) {
            String suffix = "_" + idx++;
            int allowedBaseLen = Math.max(1, SHEET_NAME_MAX_LEN - suffix.length());
            candidate = truncateSheetName(base, allowedBaseLen) + suffix;
        }
        usedSheetNames.add(candidate);
        return candidate;
    }

    private String truncateSheetName(String name) {
        return truncateSheetName(name, SHEET_NAME_MAX_LEN);
    }

    private String truncateSheetName(String name, int maxLen) {
        if (name == null || name.isBlank()) {
            return "Sheet";
        }
        String n = name.trim();
        if (n.length() <= maxLen) {
            return n;
        }
        return n.substring(0, maxLen);
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

    private static final class SheetCursor {
        private final Sheet sheet;
        private int rowIndex;

        private SheetCursor(Sheet sheet, int rowIndex) {
            this.sheet = sheet;
            this.rowIndex = rowIndex;
        }
    }

    private void closeIterator(Iterator<List<Object>> rows) {
        if (!(rows instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            log.debug("failed to close row iterator", ex);
        }
    }
}
