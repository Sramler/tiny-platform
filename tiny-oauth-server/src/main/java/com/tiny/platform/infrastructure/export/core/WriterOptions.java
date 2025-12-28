package com.tiny.platform.infrastructure.export.core;

import java.util.Map;

/**
 * WriterOptions —— 传给 WriterAdapter 的可选参数集合
 * extras 可用于将 topInfoRows、sumMap、leafFields 等传入写入层
 */
public class WriterOptions {
    private String sheetName = "Sheet1";
    private String fileName;
    private Map<String, Object> extras;

    public String getSheetName() { return sheetName; }
    public void setSheetName(String sheetName) { this.sheetName = sheetName; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Map<String, Object> getExtras() { return extras; }
    public void setExtras(Map<String, Object> extras) { this.extras = extras; }
}