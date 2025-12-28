package com.tiny.platform.infrastructure.export.util;

import com.tiny.platform.infrastructure.export.core.ColumnNode;
import java.util.ArrayList;
import java.util.List;

/**
 * HeaderBuilder —— 将 ColumnNode 树转换为 Excel 所需的 head（List<List<String>>）格式
 *
 * 输出：
 *  - head: 每个内部 List<String> 表示一列的多级标题（从顶层到最底层）
 *  - leafFields: 叶子列对应的字段名（顺序决定数据列的顺序）
 *
 * 注意：需要保证每个列（内部 List）长度一致，短的以空字符串填充。
 */
public class HeaderBuilder {

    public static HeadAndFields build(List<ColumnNode> columns) {
        List<List<String>> head = new ArrayList<>();
        List<String> leafFields = new ArrayList<>();
        List<List<String>> paths = new ArrayList<>();
        collectPaths(columns, new ArrayList<>(), paths, leafFields);

        int maxDepth = paths.stream().mapToInt(List::size).max().orElse(1);

        for (List<String> p : paths) {
            List<String> col = new ArrayList<>(p);
            while (col.size() < maxDepth) col.add("");
            head.add(col);
        }
        return new HeadAndFields(head, leafFields);
    }

    private static void collectPaths(List<ColumnNode> nodes, List<String> prefix, List<List<String>> outPaths, List<String> leafFields) {
        if (nodes == null || nodes.isEmpty()) return;
        for (ColumnNode node : nodes) {
            List<String> next = new ArrayList<>(prefix);
            next.add(node.getTitle() == null ? "" : node.getTitle());
            if (node.getChildren() == null || node.getChildren().isEmpty()) {
                outPaths.add(next);
                leafFields.add(node.getField());
            } else {
                collectPaths(node.getChildren(), next, outPaths, leafFields);
            }
        }
    }

    public static class HeadAndFields {
        public final List<List<String>> head;
        public final List<String> leafFields;
        public HeadAndFields(List<List<String>> head, List<String> leafFields) {
            this.head = head;
            this.leafFields = leafFields;
        }
    }
}