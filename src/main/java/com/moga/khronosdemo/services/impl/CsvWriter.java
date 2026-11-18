package com.moga.khronosdemo.services.impl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Builds a CSV file the way Spanish spreadsheet apps open it directly: UTF-8 with a byte order mark,
 * ';' between cells and CRLF line ends. A cell holding ';', '"' or a line break is quoted, with its
 * quotes doubled (RFC 4180).
 */
final class CsvWriter {

    private static final char SEPARATOR = ';';
    private static final String LINE_END = "\r\n";
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    // A spreadsheet treats a cell starting with one of these as a formula (CSV injection)
    private static final String FORMULA_STARTS = "=+-@\t\r";

    private final StringBuilder csv = new StringBuilder();

    CsvWriter row(String... cells) {
        csv.append(Arrays.stream(cells).map(CsvWriter::escape).collect(Collectors.joining(String.valueOf(SEPARATOR))))
                .append(LINE_END);
        return this;
    }

    byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BOM);
        out.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    /**
     * Free text that users typed, such as names. A leading =, +, -, @, tab or carriage return gets a
     * leading apostrophe, so a spreadsheet shows it as text instead of running it. Numbers the API
     * writes itself (a negative difference, for example) must not go through here.
     */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static String escape(String cell) {
        if (cell == null) {
            return "";
        }
        boolean needsQuotes = cell.indexOf(SEPARATOR) >= 0 || cell.indexOf('"') >= 0
                || cell.indexOf('\n') >= 0 || cell.indexOf('\r') >= 0;
        return needsQuotes ? '"' + cell.replace("\"", "\"\"") + '"' : cell;
    }
}
