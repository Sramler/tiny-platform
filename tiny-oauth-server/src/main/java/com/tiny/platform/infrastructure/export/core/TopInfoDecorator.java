package com.tiny.platform.infrastructure.export.core;

import java.util.List;

/**
 * TopInfoDecorator —— 顶部信息生成器接口
 *
 * 说明：
 *  - 返回值格式：List<List<String>>，外层为列，内层为该列的多行顶部信息（从上到下）
 *  - 例如：如果有三列，且要显示两行顶部信息，则返回示例：
 *      [ ["机构","机构","机构"], ["导出人","导出人","导出人"] ]  —— 但通常我们在 HeaderBuilder 方向把每列对应的行放到列内
 *
 * 实现建议：
 *  - 简单场景可返回同样的一组顶部信息（每列复制）
 *  - 复杂场景可返回每列不同信息（如统计值映射到特定列）
 */
public interface TopInfoDecorator {
    List<List<String>> getTopInfoRows(ExportRequest request, String exportType);
}