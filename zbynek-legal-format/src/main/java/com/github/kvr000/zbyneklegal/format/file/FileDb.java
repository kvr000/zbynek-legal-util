package com.github.kvr000.zbyneklegal.format.file;

import java.io.IOException;
import java.nio.file.Path;


public interface FileDb
{
	public Path findFile(String name) throws IOException;
}
