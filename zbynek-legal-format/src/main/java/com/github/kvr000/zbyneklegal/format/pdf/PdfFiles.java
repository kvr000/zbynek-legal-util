package com.github.kvr000.zbyneklegal.format.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;

import javax.inject.Singleton;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;


@Singleton
public class PdfFiles
{
	public File saveToTmp(PDDocument doc) throws IOException
	{
		File tmp = File.createTempFile("tmp", ".pdf");
		tmp.deleteOnExit();
		try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(tmp.toPath()))) {
			doc.save(stream);
		}
		return tmp;
	}

	public void saveViaTmp(PDDocument doc, Path output) throws IOException
	{
		File tmp = saveToTmp(doc);
		try {
			Files.copy(tmp.toPath(), output, StandardCopyOption.REPLACE_EXISTING);
		}
		finally {
			tmp.delete();
		}
	}
}
