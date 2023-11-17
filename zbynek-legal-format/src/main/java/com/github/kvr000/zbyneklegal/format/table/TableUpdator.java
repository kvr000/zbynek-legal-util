package com.github.kvr000.zbyneklegal.format.table;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;


public interface TableUpdator extends Closeable
{
    public Map<String, Integer> getHeaders();

    public Map<String, Map<String, String>> listEntries();

    public String getUrl(String id, String key);

    public void setValue(String id, String key, String value);

    public void save() throws IOException;

    @Override
    public void close();
}
