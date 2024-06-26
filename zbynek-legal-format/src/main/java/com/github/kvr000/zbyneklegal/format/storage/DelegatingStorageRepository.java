package com.github.kvr000.zbyneklegal.format.storage;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DelegatingStorageRepository implements StorageRepository
{
    private static final Pattern URL_PREFIX_PATTERN = Pattern.compile("^(\\w+://[^/]+/).*");

    private final Map<String, Supplier<StorageRepository>> repositories;

    @Inject
    public DelegatingStorageRepository(Map<String, Supplier<StorageRepository>> repositories)
    {
        this.repositories = repositories;
    }

    @Override
    public InputStream downloadFile(String url) throws IOException
    {
        return findRepository(url).downloadFile(url);
    }

    @Override
    public Map<String, String> metadata(String url) throws IOException
    {
        return findRepository(url).metadata(url);
    }

    private StorageRepository findRepository(String url) throws IOException
    {
        Matcher urlMatch = URL_PREFIX_PATTERN.matcher(url);
        if (!urlMatch.matches()) {
            throw new IOException("Unrecognized URL, expecting " + URL_PREFIX_PATTERN.pattern() + " got: " + url);
        }
        Supplier<StorageRepository> repository = repositories.get(urlMatch.group(1));
        if (repository == null) {
            throw new IOException("Unsupported storage repository for URL: " + url);
        }
        try {
            return repository.get();
        }
        catch (Exception ex) {
            throw new IOException(ex);
        }
    }
}
