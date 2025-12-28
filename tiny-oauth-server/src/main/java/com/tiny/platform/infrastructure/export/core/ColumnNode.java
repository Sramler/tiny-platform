package com.tiny.platform.infrastructure.export.core;

import java.util.List;
import java.util.Map;

/**
 * ColumnNode —— 多级表头树节点
 * 说明：
 *  - title: 表头显示文本（每一层）
 *  - field: 叶子列关联的数据字段名（用于从数据对象/Map中取值）
 *  - children: 子节点（为空或 null 表示叶子）
 *  - meta: 可扩展元数据，例如 {"sum":"true","format":"0.00"}
 */
public class ColumnNode {
    private String title;
    private String field;
    private List<ColumnNode> children;
    private Map<String, String> meta;

    public ColumnNode() {}

    public ColumnNode(String title, String field, List<ColumnNode> children) {
        this.title = title;
        this.field = field;
        this.children = children;
    }

    // getters / setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public List<ColumnNode> getChildren() { return children; }
    public void setChildren(List<ColumnNode> children) { this.children = children; }
    public Map<String, String> getMeta() { return meta; }
    public void setMeta(Map<String, String> meta) { this.meta = meta; }
}