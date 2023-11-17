package com.github.kvr000.zbyneklegal.format.storage.googledrive;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DelegatingStorageRepository implements StorageRepository
{
    private static final Pattern URL_PREFIX_PATTERN = Pattern.compile("^(\\w+://[^/]+/).*");

    private final Map<String, StorageRepository> repositories;

    @Inject
    public DelegatingStorageRepository(Map<String, StorageRepository> repositories)
    {
        this.repositories = repositories;
    }

    @Override
    public InputStream downloadFile(String url) throws IOException
    {
        return findRepository(url).downloadFile(url);
    }

    @Override
    public Map.Entry<String, String> checksum(String url) throws IOException
    {
        return findRepository(url).checksum(url);
    }

    private StorageRepository findRepository(String url) throws IOException
    {
        Matcher urlMatch = URL_PREFIX_PATTERN.matcher(url);
        if (!urlMatch.matches()) {
            throw new IOException("Unrecognized URL, expecting " + URL_PREFIX_PATTERN.pattern() + " got: " + url);
        }
        StorageRepository repository = repositories.get(urlMatch.group(1));
        if (repository == null) {
            throw new IOException("Unsupported storage repository for URL: " + url);
        }
        return repository;
    }
}
