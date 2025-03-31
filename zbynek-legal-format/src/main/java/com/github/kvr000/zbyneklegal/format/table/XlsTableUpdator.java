package com.github.kvr000.zbyneklegal.format.table;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileNotFoundException;
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
    public static final DataFormatter DATA_FORMATTER = new DataFormatter();

    private final Workbook workbook;

    private final Sheet sheet;

    private String[] headerNames;

    private final ImmutableMap<String, Integer> configToRows;
    private List<Map<String, String>> records;
    private Map<String, Map<String, String>> values;

    private Map<String, Integer> idToRow;

    public XlsTableUpdator(Path path, String sheetName, String idColumn) throws IOException
    {
        super(path, idColumn);

        try (InputStream reader = Files.newInputStream(path)) {
            workbook = new XSSFWorkbook(reader);
            sheet = sheetName == null ? workbook.getSheetAt(0) : workbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IOException("Cannot find sheet with name and id: sheet=" + sheetName + " idColumns=" + idColumn);
            }
            Row header = sheet.getRow(0);
            headerNames = spareMapToList(StreamSupport.stream(header.spliterator(), false)
                    .map(cell -> Optional.ofNullable(DATA_FORMATTER.formatCellValue(cell))
                            .filter(StringUtils::isNotEmpty)
                            .map(v -> Map.entry(cell.getColumnIndex(), v))
                            .orElse(null)
                    )
                    .filter(Objects::nonNull)
                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue)));
            headers = Streams.zip(
                            Stream.of(headerNames),
                            IntStream.range(0, Integer.MAX_VALUE).boxed(),
                            AbstractMap.SimpleImmutableEntry::new
                    )
                    .filter(p -> p.getKey() != null)
                    .collect(ImmutableMap.toImmutableMap(p -> p.getKey(), p -> p.getValue()));
            Integer datePosition = Optional.ofNullable(headers.get("Date")).orElseThrow(() -> new IOException("Header not found: Date"));

            Integer keyPosition = headers.get(this.idColumn);
            if (keyPosition == null) {
                throw new IllegalArgumentException("Key not found in XLS file: " + this.idColumn);
            }
            configToRows = IntStream.rangeClosed(sheet.getFirstRowNum(), sheet.getLastRowNum())
                    .mapToObj(rowId -> Optional.ofNullable(sheet.getRow(rowId))
                            .map(row -> row.getCell(datePosition))
                            .filter(Objects::nonNull)
                            .filter(cell -> cell.getCellType() == CellType.STRING)
                            .map(cell -> cell.getStringCellValue())
                            .filter(v -> StringUtils.isAlpha(v) && StringUtils.isAllUpperCase(v))
                            .map(v -> Map.entry(v, rowId))
                            .orElse(null)
                    )
                    .filter(Objects::nonNull)
                    .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

            records = IntStream.rangeClosed(sheet.getFirstRowNum(), sheet.getLastRowNum())
                    .mapToObj(rowId -> sheet.getRow(rowId))
                    .skip(1)
                    .map(row -> row == null ? Collections.<String, String>emptyMap() : StreamSupport.stream(row.spliterator(), false)
                            .filter(cell -> cell.getColumnIndex() < headerNames.length)
                            .filter(cell -> headerNames[cell.getColumnIndex()] != null)
                            .collect(ImmutableMap.toImmutableMap(
                                    cell -> headerNames[cell.getColumnIndex()],
                                    cell -> Strings.nullToEmpty(getCellString(cell))
                            ))
                    )
                    .toList();
            values = records.stream()
                    .filter(rec -> !Strings.isNullOrEmpty(rec.get(this.idColumn)))
                    .collect(ImmutableMap.toImmutableMap(
                            rec -> rec.get(this.idColumn),
                            rec -> rec
                    ));
            idToRow = Streams.zip(
                            records.stream(),
                            IntStream.rangeClosed(1, Integer.MAX_VALUE).boxed(),
                            AbstractMap.SimpleImmutableEntry::new
                    )
                    .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey().get(this.idColumn), entry.getValue()))
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
        Optional<Cell> cell = getOptionalCell(id, key);
        Optional<Hyperlink> hyperlink = cell.map(Cell::getHyperlink);
        return hyperlink.map(Hyperlink::getAddress).orElse(null);
    }

    @Override
    public Optional<String> getOptionalUrl(String id, String key)
    {
        Optional<Cell> cell = getOptionalCell(id, key);
        return cell.map(Cell::getHyperlink).map(Hyperlink::getAddress);
    }

    @Override
    public String getOptionalValue(String id, String key) {
        return Optional.ofNullable(values.get(id))
                .map(record -> record.get(key))
                .orElse(null);
    }

    @Override
    public String getConfig(String config, String key)
    {
        return getOptionalConfig(config, key)
                .orElseThrow(() -> new IllegalArgumentException("No config found for key: config=" + config + " key=" + key));
    }

    @Override
    public Optional<String> getOptionalConfig(String config, String key)
    {
        return Optional.ofNullable(configToRows.get(config))
                .map(configRow -> sheet.getRow(configRow))
                .map(row -> row.getCell(headers.get(key)))
                .map(cell -> DATA_FORMATTER.formatCellValue(cell))
                .filter(StringUtils::isNotEmpty);
    }

    public void setValue(String id, String key, String value)
    {
        try {
            upsertCell(id, key).setCellValue(value);
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Trying to access unknown column: " + "id=" + id + " key=" + key, ex);
        }
    }

    public Map<String, Map<String, String>> readSheet(String name, String key) throws IOException
    {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null) {
            throw new FileNotFoundException("Sheet not found: sheet=" + name);
        }
        Row header = sheet.getRow(0);
        String[] names = spareMapToList(StreamSupport.stream(header.spliterator(), false)
                .collect(ImmutableMap.toImmutableMap(cell -> cell.getColumnIndex(), cell -> getCellString(cell))));
        Map<String, Integer> headersMap = Streams.zip(
                        Stream.of(names),
                        IntStream.range(0, Integer.MAX_VALUE).boxed(),
                        AbstractMap.SimpleImmutableEntry::new
                )
                .filter(p -> p.getKey() != null)
                .collect(ImmutableMap.toImmutableMap(p -> p.getKey(), p -> p.getValue()));
        Integer keyPosition = headersMap.get(key);
        if (keyPosition == null) {
            throw new IllegalArgumentException("Key not found in XLS file: sheet=" + name + " key=" + key);
        }

        return StreamSupport.stream(sheet.spliterator(), false)
                .skip(1)
                .map(row -> row == null ? Collections.<String, String>emptyMap() : StreamSupport.stream(row.spliterator(), false)
                        .filter(cell -> names[cell.getColumnIndex()] != null)
                        .collect(ImmutableMap.toImmutableMap(
                                cell -> names[cell.getColumnIndex()],
                                cell -> Strings.nullToEmpty(getCellString(cell))
                        ))
                )
                .filter(row -> !Strings.isNullOrEmpty(row.get(key)))
                .collect(ImmutableMap.toImmutableMap(
                        row -> row.get(key),
                        row -> row
                ));
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

    private Cell upsertCell(String id, String key)
    {
        Integer row = idToRow.get(id);
        if (row == null) {
            throw new IllegalArgumentException("Trying to access invalid id: " + id);
        }
        try {
            return Objects.requireNonNull(sheet.getRow(row).getCell(headers.get(key), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Trying to access unknown column: " + "id=" + id + " key=" + key, ex);
        }
    }


    private Optional<Cell> getOptionalCell(String id, String key)
    {
        Integer row = idToRow.get(id);
        if (row == null) {
            throw new IllegalArgumentException("Trying to access invalid id: " + id);
        }
        try {
            return Optional.ofNullable(sheet.getRow(row).getCell(headers.get(key)));
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Trying to access unknown column: " + "id=" + id + " key=" + key, ex);
        }
    }

    private Cell needCell(String id, String key)
    {
        Integer row = idToRow.get(id);
        if (row == null) {
            throw new IllegalArgumentException("Trying to access invalid id: " + id);
        }
        try {
            return Objects.requireNonNull(sheet.getRow(row).getCell(headers.get(key), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK));
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("Trying to access unknown column: " + "id=" + id + " key=" + key, ex);
        }
    }

    private Optional<Cell> getOptionalCell(String id, String key)
    {
        Integer row = idToRow.get(id);
        if (row == null) {
            throw new IllegalArgumentException("Trying to access invalid id: " + id);
        }
        try {
            return Optional.ofNullable(sheet.getRow(row).getCell(headers.get(key)));
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
