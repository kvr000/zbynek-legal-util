package com.github.kvr000.zbyneklegal.format.pdf;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;


@Log4j2
@RequiredArgsConstructor
public class PdfRenderer implements AutoCloseable
{
	private final PDDocument document;

	public void renderPageNumber(PDPageContentStream contentStream, PDPage page, int number) throws IOException
	{
		String message = String.format("Pg %03d", number);

		PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
		float fontSize = 20.0f;

		PDRectangle pageSize = page.getMediaBox();
		float stringWidth = font.getStringWidth(message) * fontSize / 1000f;
		float stringHeight = font.getBoundingBox().getHeight() * fontSize / 1000f;

		int rotation = page.getRotation();
		boolean rotate = rotation == 90 || rotation == 270;
		float pageWidth = rotate ? pageSize.getHeight() : pageSize.getWidth();
		float pageHeight = rotate ? pageSize.getWidth() : pageSize.getHeight();

		float xPosition = rotate ? (stringHeight + 10) : (pageWidth - stringWidth - 10);
		float yPosition = rotate ? (pageWidth - stringWidth - 10) : (pageHeight - stringHeight - 10);
		log.info("Page number: page={} sw={} sh={} width={} height={} x={} y={}\n",
				number,
				stringWidth, stringHeight,
				pageWidth, pageHeight,
				xPosition, yPosition
		);
		// append the content to the existing stream
		contentStream.beginText();
		// set font and font size
		contentStream.setFont(font, fontSize);
		// set text color to red
		contentStream.setNonStrokingColor(0.5f, 0.5f, 1);
		if (rotate) {
			// rotate the text according to the page rotation
			contentStream.setTextMatrix(Matrix.getRotateInstance(Math.PI / 2, xPosition, yPosition));
		} else {
			contentStream.setTextMatrix(Matrix.getTranslateInstance(xPosition, yPosition));
		}
		contentStream.showText(message);
		contentStream.endText();
	}

	@Override
	public void close()
	{
	}
}
