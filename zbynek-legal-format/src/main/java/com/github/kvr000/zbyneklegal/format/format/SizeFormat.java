package com.github.kvr000.zbyneklegal.format.format;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SizeFormat
{
    private static Pattern SIZE_RE = Pattern.compile("^(\\d+)([BKMGT])$");

    public static long parseSize(String value)
    {
        Matcher matcher = SIZE_RE.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid value for size, expected number followed by one of BKMGT, got: " + value);
        }
        long size = Long.parseLong(matcher.group(1));
        switch (matcher.group(2)) {
        case "B":
            return size;

        case "K":
            return size * 1024;

        case "M":
            return size * (1024*1024);

        case "G":
            return size * (1024*1024*1024);

        case "T":
            return size * (1024*1024*1024*1024L);

        default:
            throw new IllegalArgumentException("Unexpected suffix: " + matcher.group(2));
        }
    }
}
