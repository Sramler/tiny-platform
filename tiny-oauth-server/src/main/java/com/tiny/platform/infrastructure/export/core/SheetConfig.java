package com.tiny.platform.infrastructure.export.core;

import java.util.List;
import java.util.Map;

/**
 * SheetConfig —— 单个 Sheet 的导出配置
 *
 * 包含了所有该 Sheet 所需信息：
 *  - sheetName: 输出在 Excel 中的 sheet 名
 *  - exportType: 绑定到系统中注册的 DataProvider 键
 *  - filters: 传给 DataProvider 的业务过滤条件（任意 map）
 *  - columns: 多级表头 ColumnNode 列表（可 null，DataProvider/服务端可回填）
 *  - aggregateKey: 该 sheet 使用的 AggregateStrategy 的 key（可 null）
 *  - options: 扩展字段，供定制使用
 */
public class SheetConfig {
    private String sheetName;
    private String exportType;
    private Map<String, Object> filters;
    private List<ColumnNode> columns;
    private String aggregateKey;
    private Map<String, Object> options;

    // getters/setters
    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }
    public String getExportType() { return exportType; }
    public void setExportType(String exportType) { this.exportType = exportType; }
    public Map<String, Object> getFilters() { return filters; }
    public void setFilters(Map<String, Object> filters) { this.filters = filters; }
    public List<ColumnNode> getColumns() { return columns; }
    public void setColumns(List<ColumnNode> columns) { this.columns = columns; }
    public String getAggregateKey() { return aggregateKey; }
    public void setAggregateKey(String aggregateKey) { this.aggregateKey = aggregateKey; }
    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }
}