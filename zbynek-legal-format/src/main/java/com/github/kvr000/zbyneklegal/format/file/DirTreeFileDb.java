package com.github.kvr000.zbyneklegal.format.file;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.mutable.MutableObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;


@RequiredArgsConstructor
public class DirTreeFileDb implements FileDb
{
	private final Path directory;

	private final String extension;

	@Override
	public Path findFile(String name) throws IOException
	{
		Path out;
		if (Files.exists(out = directory.resolve(name))) {
			return out;
		}
		String extended = extension != null ? name + extension : null;
		if (extended != null && Files.exists(out = directory.resolve(name + extension))) {
			return out;
		}
		MutableObject<Path> found = new MutableObject<>();
		Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
			{
				String filename = file.getFileName().toString();
				if (filename.equals(name) || filename.equals(extended)) {
					found.setValue(file);
					return FileVisitResult.TERMINATE;
				}
				return FileVisitResult.CONTINUE;
			}
		});
		if (found.getValue() != null) {
			return found.getValue();
		}
		throw new FileNotFoundException("File not found: " + name);
	}
}
