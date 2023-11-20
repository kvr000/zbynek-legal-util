package com.github.kvr000.zbyneklegal.format.indexfile;

import com.github.kvr000.zbyneklegal.format.table.TableUpdator;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;



@RequiredArgsConstructor
public class IndexReader
{
    private final TableUpdator table;

    @SneakyThrows
    public Map<String, Map<String, String>> readIndex(Collection<String> exhibitKeys) throws IOException
    {
        Map<String, Integer> headers = table.getHeaders();
        HashSet<String> keysSet = exhibitKeys == null ? null : exhibitKeys.stream()
                .map(key -> {
                    String fullname = key + " Exh";
                    if (!headers.containsKey(fullname)) {
                        throw sneakyThrow(new IOException("Header not found in index file: " + fullname));
                    }
                    return fullname;
                })
                .collect(Collectors.toCollection(HashSet::new));
        return table.listEntries().entrySet().stream()
                .filter(rec -> !rec.getKey().equals("BASE"))
                .filter(rec -> keysSet == null ? true : keysSet.stream()
                        .map(key -> rec.getValue().get(key))
                        .map(Strings::emptyToNull)
                        .filter(Objects::nonNull)
                        .anyMatch(s -> !s.startsWith("exclude"))
                )
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SneakyThrows
    static RuntimeException sneakyThrow(Throwable ex)
    {
        throw ex;
    }
}
