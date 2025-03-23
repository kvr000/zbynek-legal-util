package com.github.kvr000.zbyneklegal.format.pdf;

import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDStream;

import javax.inject.Singleton;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;


@Singleton
@Log4j2
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

	public PDDocument load(Path input) throws IOException
	{
		try {
			PDDocument doc = Loader.loadPDF(input.toFile());
			return doc;
		}
		catch (IOException ex) {
			throw new IOException("Failed to load PDF: " + input, ex);
		}
	}

	public int countPages(Path input) throws IOException
	{
		try (PDDocument doc = load(input)) {
			return doc.getNumberOfPages();
		}
	}

	public void decompress(PDDocument doc)
	{
		doc.setAllSecurityToBeRemoved(true);
		COSDocument cosDocument = doc.getDocument();
		cosDocument.getXrefTable().keySet()
			.forEach(o -> decompressObject(cosDocument.getObjectFromPool(o)));
		doc.getDocumentCatalog();
		doc.getDocument().setIsXRefStream(false);
	}

	private void decompressObject(COSObject cosObject)
	{
		COSBase base = cosObject.getObject();
		if (base instanceof COSStream stream) {
			if (COSName.XOBJECT.equals(stream.getItem(COSName.TYPE))
				&& COSName.IMAGE.equals(stream.getItem(COSName.SUBTYPE)))
			{
				return;
			}
			try {
				byte[] bytes = new PDStream(stream).toByteArray();
				stream.removeItem(COSName.FILTER);
				try (OutputStream streamOut = stream.createOutputStream()) {
					streamOut.write(bytes);
				}
			}
			catch (IOException ex) {
				log.warn("decompress: skip object={} generation={}", cosObject.getObjectNumber(), cosObject.getGenerationNumber(), ex);
			}
		}
	}
}
