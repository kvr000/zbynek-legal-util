package com.github.kvr000.zbyneklegal.format.indexfile;

import com.github.kvr000.zbyneklegal.format.table.TableUpdator;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.dryuf.base.exception.ExceptionUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


@RequiredArgsConstructor
public class IndexReader
{
    private static final Set<String> CONFIG_KEYS = Set.of("TABMAP", "BASE", "FILES");

    private final Map<String, Integer> headers;

    private final Map<String, Map<String, String>> entries;

    public IndexReader(TableUpdator table)
    {
        this.headers = table.getHeaders();
        this.entries = table.listEntries().entrySet().stream()
                .filter(rec -> !CONFIG_KEYS.contains(rec.getKey()))
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SneakyThrows
    public Map<String, Map<String, String>> readIndex(Collection<String> exhibitKeys) throws IOException
    {
        if (exhibitKeys != null) {
            exhibitKeys.forEach(key -> {
                        String fullname = key + " Exh";
                        if (!headers.containsKey(fullname)) {
                            throw ExceptionUtil.sneakyThrow(new IOException("Header not found in index file: " + fullname));
                        }
                    });
        }

        return entries.entrySet().stream()
                .filter(rec -> isExhibitIncluded(rec.getKey(), exhibitKeys))
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public boolean isExhibitIncluded(String id, Collection<String> exhibitKeys)
    {
        return exhibitKeys == null || exhibitKeys.stream()
                .map(key -> this.entries.get(id).get(key + " Exh"))
                .map(Strings::emptyToNull)
                .filter(Objects::nonNull)
                .anyMatch(s -> !s.equals("exclude") && !s.startsWith("exclude:") && !s.startsWith("ref:"));
    }
}
