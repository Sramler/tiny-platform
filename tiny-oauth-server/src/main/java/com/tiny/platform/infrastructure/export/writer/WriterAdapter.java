package com.tiny.platform.infrastructure.export.writer;

import com.tiny.platform.infrastructure.export.service.SheetWriteModel;

import java.io.OutputStream;
import java.util.List;

/**
 * WriterAdapter 接口
 */
public interface WriterAdapter {
    void writeMultiSheet(OutputStream out, List<SheetWriteModel> sheets) throws Exception;
}