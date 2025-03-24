package com.github.kvr000.zbyneklegal.format.table;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class TsvUtil
{
	public static String formatTsv(Object... data)
	{
		return Stream.of(data).map(TsvUtil::formatTsvSingle).collect(Collectors.joining("\t"));
	}

	public static String formatTsvSingle(Object data)
	{
		if (data == null) {
			return "";
		}
		else {
			String s = data.toString();
			if (s.contains("\t") || s.contains("\n") || s.contains("\"")) {
				return "\"" + s.replace("\"", "\"\"");
			}
			return s;
		}
	}
}
