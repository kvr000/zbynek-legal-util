package com.github.kvr000.zbyneklegal.format.table;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;


public interface TableUpdator extends Closeable
{
    public Map<String, Integer> getHeaders();

    public Map<String, Map<String, String>> listEntries();

    public String getUrl(String id, String key);

    public String getOptionalValue(String id, String key);

    public String getConfig(String config, String key);

    public Optional<String> getOptionalConfig(String config, String key);

    public void setValue(String id, String key, String value);

    default public Map<String, Map<String, String>> readSheet(String sheetName, String key) throws IOException
    {
        throw new FileNotFoundException("Sheets not supported");
    }

    public void save() throws IOException;

    @Override
    public void close();
}
