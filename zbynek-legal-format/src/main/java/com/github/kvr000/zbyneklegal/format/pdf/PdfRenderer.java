package com.github.kvr000.zbyneklegal.format.pdf;

import com.google.common.collect.ImmutableList;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.IntStream;


@Log4j2
public class PdfRenderer implements AutoCloseable
{
	private final PDDocument document;

	private final PDFMergerUtility merger;

	public PdfRenderer(PDDocument document)
	{
		this.document = document;
		this.merger = new PDFMergerUtility();
	}

	public boolean isRotated(PDPage page)
	{
		int rotation = page.getRotation();
		boolean rotate = rotation == 90 || rotation == 270;
		return rotate;
	}

	public float rotatedWidth(PDPage page)
	{
		PDRectangle pageSize = page.getMediaBox();
		float pageWidth = isRotated(page) ? pageSize.getHeight() : pageSize.getWidth();

		return pageWidth;

	}

	public float rotatedHeight(PDPage page)
	{
		PDRectangle pageSize = page.getMediaBox();
		float pageHeight = isRotated(page) ? pageSize.getWidth() : pageSize.getHeight();

		return pageHeight;
	}

	public void renderPageNumber(PDPageContentStream contentStream, PDPage page, int number) throws IOException
	{
		renderPageNumber(contentStream, page, "Pg %03d", number);
	}

	public void renderPageNumber(PDPageContentStream contentStream, PDPage page, String pattern, int number) throws IOException
	{
		String message = String.format(pattern, number);

		PDRectangle pageSize = page.getMediaBox();

		int rotation = page.getRotation();
		boolean rotate = rotation == 90 || rotation == 270;
		float pageWidth = rotate ? pageSize.getHeight() : pageSize.getWidth();
		float pageHeight = rotate ? pageSize.getWidth() : pageSize.getHeight();

		PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		float fontSize = 20.0f * (pageHeight / 792.0f);

		float stringWidth = font.getStringWidth(message) * fontSize / 1000f;
		float stringHeight = font.getBoundingBox().getHeight() * fontSize / 1000f;

		float xPosition = rotate ? (stringHeight + 10) : (pageWidth - stringWidth - 20);
		float yPosition = rotate ? (pageWidth - stringWidth - 20) : (pageHeight - stringHeight - 10);
		log.info("Page number: page={} sw={} sh={} width={} height={} x={} y={}",
			number,
			stringWidth, stringHeight,
			pageWidth, pageHeight,
			xPosition, yPosition
		);
		// append the content to the existing stream
		contentStream.beginText();
		contentStream.setFont(font, fontSize);
		contentStream.setNonStrokingColor(0.1f, 0.1f, 0.5f);
		if (rotate) {
			// rotate the text according to the page rotation
			contentStream.setTextMatrix(Matrix.getRotateInstance(Math.PI / 2, xPosition, yPosition));
		} else {
			contentStream.setTextMatrix(Matrix.getTranslateInstance(xPosition, yPosition));
		}
		contentStream.showText(message);
		contentStream.endText();
	}

	public void renderPageNumberAt(PDPageContentStream contentStream, PDPage page, float[] position, String pattern, int number) throws IOException
	{
		if (position == null) {
			renderPageNumber(contentStream, page, pattern, number);
			return;
		}
		String message = String.format(pattern, number);

		PDRectangle pageSize = page.getMediaBox();

		int rotation = page.getRotation();
		boolean rotate = rotation == 90 || rotation == 270;
		float pageWidth = rotate ? pageSize.getHeight() : pageSize.getWidth();
		float pageHeight = rotate ? pageSize.getWidth() : pageSize.getHeight();

		PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		float fontSize = 20.0f * (pageHeight / 792.0f);

		float stringWidth = font.getStringWidth(message) * fontSize / 1000f;
		float stringHeight = font.getBoundingBox().getHeight() * fontSize / 1000f;

		float xPosition = rotate ? (position[1]*pageHeight) : (position[0]*pageWidth);
		float yPosition = rotate ? (position[0]*pageWidth) : (position[1]*pageHeight);
		log.info("Page number: page={} sw={} sh={} width={} height={} x={} y={}",
			number,
			stringWidth, stringHeight,
			pageWidth, pageHeight,
			xPosition, yPosition
		);
		// append the content to the existing stream
		contentStream.beginText();
		contentStream.setFont(font, fontSize);
		contentStream.setNonStrokingColor(0.1f, 0.1f, 0.5f);
		if (rotate) {
			// rotate the text according to the page rotation
			contentStream.setTextMatrix(Matrix.getRotateInstance(Math.PI / 2, xPosition, yPosition));
		} else {
			contentStream.setTextMatrix(Matrix.getTranslateInstance(xPosition, yPosition));
		}
		contentStream.showText(message);
		contentStream.endText();
	}

	public void renderMultiLine(PDPageContentStream contentStream, PDPage page, float x, float y, float height, String message) throws IOException
	{
		int rotation = page.getRotation();
		boolean rotate = rotation == 90 || rotation == 270;
		for (String line: message.split("\n")) {
			if (rotate) {
				// rotate the text according to the page rotation
				contentStream.setTextMatrix(Matrix.getRotateInstance(Math.PI / 2, x, y));
				x += height;
			} else {
				contentStream.setTextMatrix(Matrix.getTranslateInstance(x, y));
				y -= height;
			}
			contentStream.showText(line);
		}
	}

	public List<PDPage> cloneSourcePages(DocumentWrapper source)
	{
		int numberOfPages = document.getNumberOfPages();
		try {
			merger.appendDocument(document, source.getFreshDocument());
			return IntStream.range(numberOfPages, document.getNumberOfPages())
					.mapToObj(document::getPage)
					.collect(ImmutableList.toImmutableList());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		finally {
			for (int i = document.getNumberOfPages(); --i >= numberOfPages; ) {
				document.removePage(i);
			}
		}
	}

	public void replacePage(int destination, PDPage source)
	{
		PDPageTree pageTree = document.getDocumentCatalog().getPages();
		pageTree.insertAfter(source, document.getPage(destination));
		pageTree.remove(destination);
	}

	public void insertBlankPage(int destination)
	{
		PDPageTree pageTree = document.getDocumentCatalog().getPages();
		pageTree.insertBefore(new PDPage(), document.getPage(destination));
	}

	public void removePage(int destination)
	{
		document.removePage(destination);
	}

	@Override
	public void close()
	{
	}
}
