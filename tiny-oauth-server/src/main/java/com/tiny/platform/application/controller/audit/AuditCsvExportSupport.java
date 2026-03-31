package com.tiny.platform.application.controller.audit;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

final class AuditCsvExportSupport {

    static final int EXPORT_MAX_ROWS = 10_000;
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    static final MediaType CSV_MEDIA_TYPE = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private AuditCsvExportSupport() {
    }

    static String buildAttachmentHeader(String prefix) {
        String filename = prefix + "-" + LocalDateTime.now().format(FILE_NAME_FORMATTER) + ".csv";
        return ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build()
            .toString();
    }

    static void writeBom(Writer writer) throws IOException {
        writer.write('\uFEFF');
    }

    static void writeRow(Writer writer, Object... values) throws IOException {
        writer.write(Arrays.stream(values)
            .map(AuditCsvExportSupport::escape)
            .collect(Collectors.joining(",")));
        writer.write(System.lineSeparator());
    }

    private static String escape(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String normalized = value.toString()
            .replace("\"", "\"\"");
        return "\"" + normalized + "\"";
    }
}
