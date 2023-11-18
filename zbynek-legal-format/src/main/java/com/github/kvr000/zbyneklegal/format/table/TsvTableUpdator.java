package com.github.kvr000.zbyneklegal.format.table;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class TsvTableUpdator extends AbstractTableUpdator
{
    public static CSVFormat PARSER_TSV_HEADER = CSVFormat.DEFAULT.builder()
            .setRecordSeparator('\n')
            .setDelimiter('\t')
            .setHeader()
            .setSkipHeaderRecord(true)
            .build();

    private Path filePath;
    private Map<String, Integer> headers;
    private List<Map<String, String>> records;
    private Map<String, Map<String, String>> values;

    public TsvTableUpdator(Path path, String idColumn) throws IOException
    {
        super(path, idColumn);

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

    public Map<String, Map<String, String>> listEntries()
    {
        return values;
    }

    @Override
    public String getUrl(String id, String key)
    {
        return null;
    }

    @Override
    public String getOptionalValue(String id, String key) {
        return Optional.ofNullable(values.get(id))
                .map(row -> row.get(key))
                .orElse(null);
    }

    @Override
    public void setValue(String id, String key, String value)
    {
        values.get(id).compute(key, (key0, old) -> {
            if (old == null) {
                throw new IllegalArgumentException("Trying to access unknown column: " + key0);
            }
            return value;
        });
    }

    @Override
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
}
