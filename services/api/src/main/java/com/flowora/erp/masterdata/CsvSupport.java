package com.flowora.erp.masterdata;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class CsvSupport {
    private CsvSupport() {
    }

    static List<List<String>> read(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8).replace("\uFEFF", "");
            return content.lines()
                    .filter(line -> !line.isBlank())
                    .map(CsvSupport::parseLine)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read CSV file", exception);
        }
    }

    static String value(List<String> row, List<String> headers, String header) {
        int index = headers.indexOf(header);
        return index >= 0 && index < row.size() ? row.get(index).trim() : "";
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\"", "\"\"");
        return normalized.contains(",") || normalized.contains("\n") || normalized.contains("\r")
                ? "\"" + normalized + "\""
                : normalized;
    }

    static String line(Object... values) {
        return java.util.Arrays.stream(values)
                .map(value -> escape(value == null ? "" : value.toString()))
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString());
        return values;
    }
}
