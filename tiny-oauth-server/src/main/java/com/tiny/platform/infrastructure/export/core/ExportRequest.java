package com.tiny.platform.infrastructure.export.core;

import java.util.List;

/**
 * ExportRequest —— 统一的导出请求，仅包含 sheets 配置
 *
 * 说明：
 *  - fileName: 最终的输出文件名（无扩展名），默认会加 .xlsx
 *  - async: 是否异步导出
 *  - pageSize: 如果 DataProvider 支持分页，作为每页大小
 *  - sheets: 必填，至少包含一个 SheetConfig
 */
public class ExportRequest {
    private String fileName;
    private boolean async = false;
    private int pageSize = 5000;
    private List<SheetConfig> sheets;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public List<SheetConfig> getSheets() { return sheets; }
    public void setSheets(List<SheetConfig> sheets) { this.sheets = sheets; }
}