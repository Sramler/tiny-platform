package com.tiny.platform.infrastructure.export.writer.fesod;

import com.tiny.platform.infrastructure.export.core.AggregateStrategy;
import com.tiny.platform.infrastructure.export.core.WriterOptions;
import com.tiny.platform.infrastructure.export.service.SheetWriteModel;
import com.tiny.platform.infrastructure.export.writer.WriterAdapter;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.support.ExcelTypeEnum;
import org.apache.fesod.sheet.write.builder.ExcelWriterBuilder;
import org.apache.fesod.sheet.write.builder.ExcelWriterSheetBuilder;
import org.apache.fesod.sheet.write.handler.SheetWriteHandler;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.apache.fesod.sheet.write.metadata.holder.WriteSheetHolder;
import org.apache.fesod.sheet.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FesodWriterAdapter —— 基于 Apache Fesod 的多 Sheet 写入实现
 *
 * 功能支持：
 *  - 多业务 Sheet 同时导出
 *  - 顶部信息行（TopInfo）+ 多级表头自动合并
 *  - 流式写入（批量 flush，降低内存）
 *  - 合计行输出（AggregateStrategy）
 *  - 可扩展 WriterAdapter 接口，与 POI 版本保持一致
 */
public class FesodWriterAdapter implements WriterAdapter {
    private static final Logger log = LoggerFactory.getLogger(FesodWriterAdapter.class);
    private static final int XLSX_MAX_ROWS = 1_048_576;
    private static final int SHEET_NAME_MAX_LEN = 31;

    public static final String EXTRA_TOP_INFO_ROWS = "topInfoRows";
    public static final String EXTRA_LEAF_FIELDS = "leafFields";
    public static final String EXTRA_SUM_MAP = "sumMap";
    public static final String EXTRA_STRATEGY = "aggregateStrategy";

    private final int batchSize;
    private final int maxRowsPerSheet;

    public FesodWriterAdapter() {
        this(1024, XLSX_MAX_ROWS);
    }

    public FesodWriterAdapter(int batchSize) {
        this(batchSize, XLSX_MAX_ROWS);
    }

    public FesodWriterAdapter(int batchSize, int maxRowsPerSheet) {
        this.batchSize = Math.max(1, batchSize);
        this.maxRowsPerSheet = Math.max(1, Math.min(maxRowsPerSheet, XLSX_MAX_ROWS));
    }

    @Override
    public void writeMultiSheet(OutputStream out, List<SheetWriteModel> sheets) throws Exception {
        if (sheets == null || sheets.isEmpty()) {
            return;
        }
        ExcelWriterBuilder builder = FesodSheet.write(out)
                .autoCloseStream(false)
                .excelType(ExcelTypeEnum.XLSX);
        ExcelWriter writer = builder.build();
        try {
            int index = 0;
            Set<String> usedSheetNames = new java.util.HashSet<>();
            for (SheetWriteModel model : sheets) {
                if (model == null) {
                    continue;
                }
                index = writeSheetModel(writer, index, usedSheetNames, model);
            }
        } finally {
            writer.finish();
        }
    }

    private int writeSheetModel(ExcelWriter writer,
                                int sheetIndex,
                                Set<String> usedSheetNames,
                                SheetWriteModel model) {
        String baseSheetName = (model.getSheetName() == null || model.getSheetName().isBlank())
                ? "Sheet" + (sheetIndex + 1)
                : model.getSheetName();
        List<List<String>> head = model.getHead();
        TopInfoPlan plan = TopInfoPlan.from(model.getTopInfoRows(), head);
        int dataStartRow = plan.topRowCount() + headerRowCount(head);
        if (dataStartRow >= maxRowsPerSheet) {
            throw new IllegalArgumentException("表头行数超过单Sheet最大行数限制: " + maxRowsPerSheet);
        }

        int part = 1;
        SheetPartCursor cursor = openSheet(sheetIndex++, baseSheetName, part, usedSheetNames, model, plan);
        Iterator<List<Object>> iterator = model.getRows();
        List<List<Object>> batch = new ArrayList<>(batchSize);
        boolean wroteData = false;
        try {
            while (iterator != null && iterator.hasNext()) {
                if (cursor.dataRowsWritten >= cursor.dataCapacity) {
                    if (!batch.isEmpty()) {
                        writer.write(batch, cursor.writeSheet);
                        batch = new ArrayList<>(batchSize);
                    }
                    cursor = openSheet(sheetIndex++, baseSheetName, ++part, usedSheetNames, model, plan);
                }
                batch.add(iterator.next());
                cursor.dataRowsWritten++;
                wroteData = true;
                if (batch.size() >= batchSize || cursor.dataRowsWritten >= cursor.dataCapacity) {
                    writer.write(batch, cursor.writeSheet);
                    batch = new ArrayList<>(batchSize);
                }
            }
            if (!batch.isEmpty()) {
                writer.write(batch, cursor.writeSheet);
            }
            if (!wroteData) {
                writer.write(Collections.emptyList(), cursor.writeSheet);
            }
            if (requiresNewSheetForSummary(cursor, model)) {
                cursor = openSheet(sheetIndex++, baseSheetName, ++part, usedSheetNames, model, plan);
            }
            writeSummaryRow(writer, cursor.writeSheet, model);
            return sheetIndex;
        } finally {
            closeIterator(iterator);
        }
    }

    private SheetWriteModel legacySheetModel(List<List<String>> head,
                                             Iterator<List<Object>> rows,
                                             WriterOptions options) {
        String sheetName = options != null && options.getSheetName() != null
                ? options.getSheetName()
                : "Sheet1";
        Map<String, Object> extras = options != null ? options.getExtras() : null;
        List<List<String>> topInfo = extras == null ? null : castTopInfo(extras.get(EXTRA_TOP_INFO_ROWS));
        List<String> leafFields = extras == null ? null : castLeafFields(extras.get(EXTRA_LEAF_FIELDS));
        @SuppressWarnings("unchecked")
        Map<String, Object> sumMap = extras == null ? null : (Map<String, Object>) extras.get(EXTRA_SUM_MAP);
        AggregateStrategy strategy = extras == null ? null : (AggregateStrategy) extras.get(EXTRA_STRATEGY);
        return new SheetWriteModel(sheetName, head, rows, topInfo, leafFields, strategy, sumMap);
    }

    private SheetPartCursor openSheet(int sheetIndex,
                                      String baseSheetName,
                                      int part,
                                      Set<String> usedSheetNames,
                                      SheetWriteModel model,
                                      TopInfoPlan plan) {
        String desiredName = buildSheetName(baseSheetName, part);
        String sheetName = resolveUniqueSheetName(desiredName, usedSheetNames);
        WriteSheet writeSheet = buildWriteSheet(sheetIndex, sheetName, model, plan);
        int dataCapacity = maxRowsPerSheet - (plan.topRowCount() + headerRowCount(model.getHead()));
        return new SheetPartCursor(writeSheet, dataCapacity);
    }

    private WriteSheet buildWriteSheet(int sheetIndex, String sheetName, SheetWriteModel model, TopInfoPlan plan) {
        ExcelWriterSheetBuilder sheetBuilder = FesodSheet.writerSheet(sheetIndex, sheetName);
        List<List<String>> head = model.getHead();
        if (head != null && !head.isEmpty()) {
            sheetBuilder.head(head);
            sheetBuilder.needHead(true);
            sheetBuilder.automaticMergeHead(true);
        } else {
            sheetBuilder.needHead(false);
        }
        if (plan.hasTopInfo()) {
            sheetBuilder.relativeHeadRowIndex(plan.topRowCount());
            sheetBuilder.registerWriteHandler(new TopInfoSheetWriteHandler(plan));
        }

        return sheetBuilder.build();
    }

    private void writeSummaryRow(ExcelWriter writer, WriteSheet writeSheet, SheetWriteModel model) {
        AggregateStrategy strategy = model.getStrategy();
        Map<String, Object> sumMap = model.getSumMap();
        if (strategy == null || sumMap == null || sumMap.isEmpty()) {
            return;
        }
        List<String> columns = leafFieldsOrHead(model.getLeafFields(), model.getHead());
        if (columns.isEmpty()) {
            return;
        }
        List<Object> summary = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String field = columns.get(i);
            if (strategy.isAggregate(field)) {
                summary.add(strategy.finalize(field, sumMap.get(field)));
            } else if (i == 0) {
                summary.add("总计");
            } else {
                summary.add(null);
            }
        }
        writer.write(Collections.singletonList(summary), writeSheet);
    }

    private boolean requiresNewSheetForSummary(SheetPartCursor cursor, SheetWriteModel model) {
        AggregateStrategy strategy = model.getStrategy();
        Map<String, Object> sumMap = model.getSumMap();
        if (strategy == null || sumMap == null || sumMap.isEmpty()) {
            return false;
        }
        return cursor.dataRowsWritten >= cursor.dataCapacity;
    }

    private int headerRowCount(List<List<String>> head) {
        if (head == null || head.isEmpty()) {
            return 0;
        }
        List<String> first = head.get(0);
        return first == null ? 0 : first.size();
    }

    private List<String> leafFieldsOrHead(List<String> leafFields, List<List<String>> head) {
        if (leafFields != null && !leafFields.isEmpty()) {
            return leafFields;
        }
        if (head == null || head.isEmpty()) {
            return List.of();
        }
        List<String> placeholder = new ArrayList<>(head.size());
        for (int i = 0; i < head.size(); i++) {
            placeholder.add("");
        }
        return placeholder;
    }

    private String buildSheetName(String baseName, int part) {
        String normalizedBase = baseName == null || baseName.isBlank() ? "Sheet" : baseName.trim();
        if (part <= 1) {
            return truncateSheetName(normalizedBase);
        }
        String suffix = "_" + part;
        int allowedBaseLen = Math.max(1, SHEET_NAME_MAX_LEN - suffix.length());
        return truncateSheetName(normalizedBase, allowedBaseLen) + suffix;
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
        String trimmed = name.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen);
    }

    private void closeIterator(Iterator<List<Object>> iterator) {
        if (!(iterator instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            log.debug("failed to close row iterator", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<List<String>> castTopInfo(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return (List<List<String>>) value;
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException("extras.topInfoRows 类型必须是 List<List<String>>", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> castLeafFields(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return (List<String>) value;
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException("extras.leafFields 类型必须是 List<String>", ex);
        }
    }

    private static final class SheetPartCursor {
        private final WriteSheet writeSheet;
        private final int dataCapacity;
        private int dataRowsWritten;

        private SheetPartCursor(WriteSheet writeSheet, int dataCapacity) {
            this.writeSheet = writeSheet;
            this.dataCapacity = dataCapacity;
        }
    }

    /**
     * 顶部信息写入方案
     */
    private static final class TopInfoPlan {
        private final int topRowCount;
        private final boolean columnWise;
        private final List<List<String>> columnData;
        private final String globalText;
        private final int headColumnCount;

        private TopInfoPlan(int topRowCount,
                            boolean columnWise,
                            List<List<String>> columnData,
                            String globalText,
                            int headColumnCount) {
            this.topRowCount = topRowCount;
            this.columnWise = columnWise;
            this.columnData = columnData;
            this.globalText = globalText;
            this.headColumnCount = headColumnCount;
        }

        static TopInfoPlan from(List<List<String>> topInfoRows, List<List<String>> head) {
            if (topInfoRows == null || topInfoRows.isEmpty()) {
                return new TopInfoPlan(0, false, null, null, head == null ? 0 : head.size());
            }
            int headColumns = head == null ? 0 : head.size();
            if (headColumns > 0 && topInfoRows.size() == headColumns) {
                int rowCount = topInfoRows.get(0) == null ? 0 : topInfoRows.get(0).size();
                if (rowCount <= 0) {
                    return new TopInfoPlan(0, false, null, null, headColumns);
                }
                List<List<String>> normalized = new ArrayList<>(headColumns);
                for (List<String> column : topInfoRows) {
                    List<String> copy = new ArrayList<>(rowCount);
                    for (int i = 0; i < rowCount; i++) {
                        String text = (column != null && i < column.size()) ? column.get(i) : "";
                        copy.add(text == null ? "" : text);
                    }
                    normalized.add(copy);
                }
                return new TopInfoPlan(rowCount, true, normalized, null, headColumns);
            }
            String merged = topInfoRows.stream()
                    .filter(Objects::nonNull)
                    .flatMap(list -> list.stream().filter(Objects::nonNull))
                    .collect(Collectors.joining(" "));
            if (merged.isBlank()) {
                return new TopInfoPlan(0, false, null, null, headColumns);
            }
            return new TopInfoPlan(1, false, null, merged, headColumns);
        }

        boolean hasTopInfo() {
            return topRowCount > 0;
        }

        int topRowCount() {
            return topRowCount;
        }
    }

    /**
     * 在 sheet 创建后写入顶部信息行
     */
    private static final class TopInfoSheetWriteHandler implements SheetWriteHandler {
        private final TopInfoPlan plan;

        private TopInfoSheetWriteHandler(TopInfoPlan plan) {
            this.plan = plan;
        }

        @Override
        public void beforeSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {}

        @Override
        public void afterSheetCreate(WriteWorkbookHolder writeWorkbookHolder, WriteSheetHolder writeSheetHolder) {
            if (!plan.hasTopInfo()) {
                return;
            }
            Sheet sheet = writeSheetHolder.getSheet();
            if (sheet == null) {
                return;
            }
            if (plan.columnWise) {
                for (int r = 0; r < plan.topRowCount; r++) {
                    Row row = getOrCreateRow(sheet, r);
                    for (int c = 0; c < plan.columnData.size(); c++) {
                        String text = plan.columnData.get(c).get(r);
                        if (text == null || text.isEmpty()) {
                            continue;
                        }
                        Cell cell = row.getCell(c);
                        if (cell == null) {
                            cell = row.createCell(c);
                        }
                        cell.setCellValue(text);
                    }
                }
            } else {
                Row row = getOrCreateRow(sheet, 0);
                Cell cell = row.getCell(0);
                if (cell == null) {
                    cell = row.createCell(0);
                }
                cell.setCellValue(plan.globalText);
                if (plan.headColumnCount > 1) {
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, plan.headColumnCount - 1));
                }
            }
        }

        private Row getOrCreateRow(Sheet sheet, int rowIndex) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }
            return row;
        }
    }
}
