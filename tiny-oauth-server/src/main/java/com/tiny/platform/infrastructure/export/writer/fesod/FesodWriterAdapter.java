package com.tiny.platform.infrastructure.export.writer.fesod;

import com.tiny.platform.infrastructure.export.core.AggregateStrategy;
import com.tiny.platform.infrastructure.export.core.WriterOptions;
import com.tiny.platform.infrastructure.export.service.SheetWriteModel;
import com.tiny.platform.infrastructure.export.writer.WriterAdapter;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.EasyExcel;
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

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * FesodWriterAdapter —— 基于 Apache Fesod (EasyExcel) 的多 Sheet 写入实现
 *
 * 功能支持：
 *  - 多业务 Sheet 同时导出
 *  - 顶部信息行（TopInfo）+ 多级表头自动合并
 *  - 流式写入（批量 flush，降低内存）
 *  - 合计行输出（AggregateStrategy）
 *  - 可扩展 WriterAdapter 接口，与 POI 版本保持一致
 */
public class FesodWriterAdapter implements WriterAdapter {

    public static final String EXTRA_TOP_INFO_ROWS = "topInfoRows";
    public static final String EXTRA_LEAF_FIELDS = "leafFields";
    public static final String EXTRA_SUM_MAP = "sumMap";
    public static final String EXTRA_STRATEGY = "aggregateStrategy";

    private final int batchSize;

    public FesodWriterAdapter() {
        this(1024);
    }

    public FesodWriterAdapter(int batchSize) {
        this.batchSize = Math.max(1, batchSize);
    }

    @Override
    public void writeMultiSheet(OutputStream out, List<SheetWriteModel> sheets) throws Exception {
        if (sheets == null || sheets.isEmpty()) {
            return;
        }
        ExcelWriterBuilder builder = EasyExcel.write(out)
                .autoCloseStream(false)
                .excelType(ExcelTypeEnum.XLSX);
        ExcelWriter writer = builder.build();
        try {
            int index = 0;
            for (SheetWriteModel model : sheets) {
                if (model == null) {
                    continue;
                }
                WriteSheet writeSheet = buildWriteSheet(index++, model);
                streamRows(writer, writeSheet, model);
                writeSummaryRow(writer, writeSheet, model);
            }
        } finally {
            writer.finish();
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

    private WriteSheet buildWriteSheet(int sheetIndex, SheetWriteModel model) {
        String sheetName = (model.getSheetName() == null || model.getSheetName().isBlank())
                ? "Sheet" + (sheetIndex + 1)
                : model.getSheetName();
        ExcelWriterSheetBuilder sheetBuilder = EasyExcel.writerSheet(sheetIndex, sheetName);
        List<List<String>> head = model.getHead();
        if (head != null && !head.isEmpty()) {
            sheetBuilder.head(head);
            sheetBuilder.needHead(true);
            sheetBuilder.automaticMergeHead(true);
        } else {
            sheetBuilder.needHead(false);
        }

        TopInfoPlan plan = TopInfoPlan.from(model.getTopInfoRows(), head);
        if (plan.hasTopInfo()) {
            sheetBuilder.relativeHeadRowIndex(plan.topRowCount());
            sheetBuilder.registerWriteHandler(new TopInfoSheetWriteHandler(plan));
        }

        return sheetBuilder.build();
    }

    private void streamRows(ExcelWriter writer, WriteSheet writeSheet, SheetWriteModel model) {
        Iterator<List<Object>> iterator = model.getRows();
        if (iterator == null) {
            writer.write(Collections.emptyList(), writeSheet);
            return;
        }
        List<List<Object>> batch = new ArrayList<>(batchSize);
        boolean wrote = false;
        while (iterator.hasNext()) {
            batch.add(iterator.next());
            if (batch.size() >= batchSize) {
                writer.write(batch, writeSheet);
                wrote = true;
                batch = new ArrayList<>(batchSize);
            }
        }
        if (!batch.isEmpty()) {
            writer.write(batch, writeSheet);
            wrote = true;
        }
        if (!wrote) {
            writer.write(Collections.emptyList(), writeSheet);
        }
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