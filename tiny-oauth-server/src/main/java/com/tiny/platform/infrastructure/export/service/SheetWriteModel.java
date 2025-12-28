package com.tiny.platform.infrastructure.export.service;

import com.tiny.platform.infrastructure.export.core.AggregateStrategy;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 单个 Sheet 写入模型
 */
public class SheetWriteModel {
    private String sheetName;
    private List<List<String>> head; // 多级表头
    private List<List<String>> topInfoRows; // 顶部信息行
    private Iterator<List<Object>> rows; // 数据迭代器
    private AggregateStrategy strategy; // 合计策略
    private Map<String,Object> sumMap; // 合计临时存储
    private List<String> leafFields; // 表头叶子字段
    private long estimatedTotalRows; // 估算总行数

    public SheetWriteModel() {
    }

    public SheetWriteModel(String sheetName, List<List<String>> head, Iterator<List<Object>> rowIterator, List<List<String>> topInfoRows, List<String> leafFields, AggregateStrategy strategy, Map<String, Object> sumMap) {
        this.head = head;
        this.rows = rowIterator;
        this.topInfoRows = topInfoRows;
        this.strategy = strategy;
        this.sumMap = sumMap;
        this.leafFields = leafFields;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public List<List<String>> getHead() {
        return head;
    }

    public void setHead(List<List<String>> head) {
        this.head = head;
    }

    public List<List<String>> getTopInfoRows() {
        return topInfoRows;
    }

    public void setTopInfoRows(List<List<String>> topInfoRows) {
        this.topInfoRows = topInfoRows;
    }

    public Iterator<List<Object>> getRows() {
        return rows;
    }

    public void setRows(Iterator<List<Object>> rows) {
        this.rows = rows;
    }

    public AggregateStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(AggregateStrategy strategy) {
        this.strategy = strategy;
    }

    public Map<String, Object> getSumMap() {
        return sumMap;
    }

    public void setSumMap(Map<String, Object> sumMap) {
        this.sumMap = sumMap;
    }

    public List<String> getLeafFields() {
        return leafFields;
    }

    public void setLeafFields(List<String> leafFields) {
        this.leafFields = leafFields;
    }

    public long getEstimatedTotalRows() {
        return estimatedTotalRows;
    }

    public void setEstimatedTotalRows(long estimatedTotalRows) {
        this.estimatedTotalRows = estimatedTotalRows;
    }
}