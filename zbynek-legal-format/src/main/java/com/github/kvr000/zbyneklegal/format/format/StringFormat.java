package com.github.kvr000.zbyneklegal.format.format;

public class StringFormat
{
	public static String escapePercent(String s)
	{
		return s.replace("%", "%%");
	}
}
