package com.github.kvr000.zbyneklegal.format.table;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


public class TsvUpdator implements Closeable
{
    public static CSVFormat PARSER_TSV_HEADER = CSVFormat.DEFAULT.builder()
            .setDelimiter('\t')
            .setHeader()
            .setSkipHeaderRecord(true)
            .build();

    private Path filePath;
    private Map<String, Integer> headers;
    private List<Map<String, String>> records;
    private Map<String, Map<String, String>> values;

    public TsvUpdator(Path path, String idColumn) throws IOException
    {
        this.filePath = path;
        try (CSVParser parser = CSVParser.parse(this.filePath, Charsets.UTF_8, PARSER_TSV_HEADER)) {
            headers = parser.getHeaderMap();
            Integer keyPosition = headers.get(idColumn);
            if (keyPosition == null) {
                throw new IllegalArgumentException("Key not found in TSV file: " + idColumn);
            }

            records = parser.stream()
                    .map(CSVRecord::toMap)
                    .toList();
            values = records.stream()
                    .filter(rec -> !Strings.isNullOrEmpty(rec.get(idColumn)))
                    .collect(ImmutableMap.toImmutableMap(
                            rec -> rec.get(idColumn),
                            rec -> rec
                    ));
        }
    }

    public Map<String, Integer> getHeaders()
    {
        return headers;
    }

    public Map<String, Map<String, String>> listEntries()
    {
        return values;
    }

    public void setValue(String id, String key, String value)
    {
        values.get(id).compute(key, (key0, old) -> {
            if (old == null) {
                throw new IllegalArgumentException("Trying to set unknown column: " + key0);
            }
            return value;
        });
    }

    public void save() throws IOException {
        StringBuilder output = new StringBuilder();
        try (CSVPrinter printer = new CSVPrinter(output, PARSER_TSV_HEADER)) {
            printer.printRecord(headers.keySet());
            for (Map<String, String> record: records) {
                printer.printRecord(headers.keySet().stream()
                        .map(record::get));
            }
            Files.writeString(filePath, output);
        }
    }

    @Override
    public void close() throws IOException
    {

    }
}
