package com.tiny.platform.infrastructure.export.core;

import java.util.Collections;
import java.util.List;

/**
 * 默认的 TopInfoDecorator 实现，返回空的顶部说明信息。
 * <p>
 * 方便业务方按需覆写，自定义导出顶部说明（如公司名称、导出人等）。
 */
public class DefaultTopInfoDecorator implements TopInfoDecorator {

    @Override
    public List<List<String>> getTopInfoRows(ExportRequest request, String exportType) {
        return Collections.emptyList();
    }
}

