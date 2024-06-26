package com.github.kvr000.zbyneklegal.format.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;


public interface StorageRepository
{
    public Map<String, String> metadata(String url) throws IOException;

    public InputStream downloadFile(String url) throws IOException;
}
