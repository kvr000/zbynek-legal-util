package com.github.kvr000.zbyneklegal.format.table;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


public class XlsTableUpdator extends AbstractTableUpdator
{
    private final Workbook workbook;

    private final Sheet sheet;

    private String[] headerNames;

    private List<Map<String, String>> records;
    private Map<String, Map<String, String>> values;

    private Map<String, Integer> idToRow;

    public XlsTableUpdator(Path path, String idColumn) throws IOException
    {
        super(path, idColumn);

            try (InputStream reader = Files.newInputStream(path)) {
                workbook = new XSSFWorkbook(reader);
                sheet = workbook.getSheetAt(0);
                Row header = sheet.getRow(0);
                headerNames = spareMapToList(StreamSupport.stream(header.spliterator(), false)
                        .collect(ImmutableMap.toImmutableMap(cell -> cell.getColumnIndex(), cell -> getCellString(cell))));
                headers = Streams.zip(
                                Stream.of(headerNames),
                                IntStream.range(0, Integer.MAX_VALUE).boxed(),
                                AbstractMap.SimpleImmutableEntry::new
                        )
                        .filter(p -> p.getKey() != null)
                        .collect(ImmutableMap.toImmutableMap(p -> p.getKey(), p -> p.getValue()));
                Integer keyPosition = headers.get(idColumn);
                if (keyPosition == null) {
                    throw new IllegalArgumentException("Key not found in XLS file: " + idColumn);
                }

                records = IntStream.rangeClosed(sheet.getFirstRowNum(), sheet.getLastRowNum())
                        .mapToObj(rowId -> sheet.getRow(rowId))
                        .skip(1)
                        .map(row -> row == null ? Collections.<String, String>emptyMap() : StreamSupport.stream(row.spliterator(), false)
                                .filter(cell -> headerNames[cell.getColumnIndex()] != null)
                                .collect(ImmutableMap.toImmutableMap(
                                        cell -> headerNames[cell.getColumnIndex()],
                                        cell -> Strings.nullToEmpty(getCellString(cell))
                                ))
                        )
                        .toList();
                values = records.stream()
                        .filter(rec -> !Strings.isNullOrEmpty(rec.get(idColumn)))
                        .collect(ImmutableMap.toImmutableMap(
                                rec -> rec.get(idColumn),
                                rec -> rec
                        ));
                idToRow = Streams.zip(
                        records.stream(),
                        IntStream.rangeClosed(1, Integer.MAX_VALUE).boxed(),
                        AbstractMap.SimpleImmutableEntry::new
                )
                        .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey().get(idColumn), entry.getValue()))
                        .filter(entry -> !Strings.isNullOrEmpty(entry.getKey()))
                        .collect(ImmutableMap.toImmutableMap(entry -> entry.getKey(), entry -> entry.getValue()));
            }
    }

    public Map<String, Map<String, String>> listEntries()
    {
        return values;
    }

    @Override
    public String getUrl(String id, String key)
    {
        Cell cell = getCell(id, key);
        Hyperlink hyperlink = cell.getHyperlink();
        return Optional.ofNullable(hyperlink).map(Hyperlink::getAddress).orElse(null);
    }

    @Override
    public String getOptionalValue(String id, String key) {
        return Optional.ofNullable(values.get(id))
                .map(record -> record.get(key))
                .orElse(null);
    }

    public void setValue(String id, String key, String value)
    {
        try {
            getCell(id, key).setCellValue(value);
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Trying to access unknown column: " + "id=" + id + " key=" + key, ex);
        }
    }

    public void save() throws IOException
    {
        try (OutputStream output = Files.newOutputStream(this.filePath)) {
            workbook.write(output);
        }
    }

    @Override
    public void close()
    {
        try {
            workbook.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Cell getCell(String id, String key)
    {
        Integer row = idToRow.get(id);
        if (row == null) {
            throw new IllegalArgumentException("Trying to access invalid id: " + id);
        }
        try {
            return Objects.requireNonNull(sheet.getRow(row).getCell(headers.get(key)));
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Trying to access unknown column: " + "id=" + id + " key=" + key, ex);
        }
    }

    private String getCellString(Cell cell)
    {
        switch (cell.getCellType()) {
        case NUMERIC:
            return (long) cell.getNumericCellValue() == cell.getNumericCellValue() ? Long.toString((long) cell.getNumericCellValue()) : Double.toString(cell.getNumericCellValue());

        case STRING:
            return cell.getStringCellValue();

        case BLANK:
            return null;

        case FORMULA:
            return cell.getCellFormula();

        default:
            return null;
        }
    }

    private static String[] spareMapToList(Map<Integer, String> map)
    {
        String[] out = new String[map.keySet().stream().reduce(-1, Integer::max)+1];
        map.forEach((i, v) -> out[i] = v);
        return out;
    }
}
