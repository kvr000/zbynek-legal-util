package com.github.kvr000.zbyneklegal.format.table;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;


@RequiredArgsConstructor
public abstract class AbstractTableUpdator implements TableUpdator
{
    protected final Path filePath;

    protected final String idColumn;

    protected Map<String, Integer> headers;

    public Map<String, Integer> getHeaders()
    {
        return headers;
    };

    public abstract Map<String, Map<String, String>> listEntries();

    public abstract void setValue(String id, String key, String value);

    public abstract void save() throws IOException;

    @Override
    public void close()
    {
    }
}
