package com.github.kvr000.zbyneklegal.format.pdf;

import com.github.kvr000.zbyneklegal.format.collection.CloseableIterator;
import com.google.common.collect.ImmutableList;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Log4j2
public class PdfRenderer implements AutoCloseable
{
	public static final float IMAGE_SCALE = 150.0f / 72.0f;

	private static final boolean HAS_PDFTOCAIRO;

	private final PDDocument document;

	private final PDFMergerUtility merger;

	static {
		int exit;
		try {
			exit = new ProcessBuilder("pdftocairo", "--help")
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start().waitFor();
		}
		catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		catch (IOException e) {
			exit = -1;
		}
		HAS_PDFTOCAIRO = exit == 0;
	}

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

	public CloseableIterator<Path> renderImages(Path inputFile, String format, Double scale, Float quality) throws IOException
	{
		Path tmp = Files.createTempDirectory("pdftocairo");
		List<Path> output;
		try {
			if (document.getNumberOfPages() == 0) {
				output = Collections.emptyList();
			}
			else if (HAS_PDFTOCAIRO && (format.equals("jpeg") || format.equals("png"))) {
				PDRectangle box = document.getPage(0).getMediaBox();
				float targetWidth = box.getWidth() * IMAGE_SCALE;
				float targetHeight = box.getHeight() * IMAGE_SCALE;
				if (scale != null) {
					targetWidth *= scale;
					targetHeight *= scale;
				}
				try {
					ImmutableList.Builder<String> args = ImmutableList.<String>builder()
						.add("pdftocairo")
						.add("-" + format);
					if (format.equals("jpeg") && quality != null) {
						args.add("-jpegopt").add("quality=" + Math.round(quality * 100));
					}
					if (scale != null) {
						args.add("-scale-to-x").add(Integer.toString(Math.round(targetWidth)));
					}
					args.add(inputFile.toString());
					args.add(tmp.toString() + File.separator);
					int exit = new ProcessBuilder(args.build())
						.redirectError(ProcessBuilder.Redirect.INHERIT)
						.redirectOutput(ProcessBuilder.Redirect.DISCARD)
						.start()
						.waitFor();
					if (exit != 0) {
						throw new IOException("Failed to execute " + Strings.join(args.build(), ' ') + ": exit=" + exit);
					}
				}
				catch (InterruptedException e) {
					throw new IOException("Failed to wait for gs", e);
				}
				output = Files.list(tmp).sorted().toList();
			}
			else {
				output = new ArrayList<>();
				PDFRenderer inkRenderer = new PDFRenderer(document);
				for (int i = 0; i < document.getNumberOfPages(); ++i) {
					BufferedImage image = inkRenderer.renderImage(i, IMAGE_SCALE);
					float targetWidth = image.getWidth();
					float targetHeight = image.getHeight();

					final BufferedImage resizedImage;
					if (scale == null || scale == 1.0) {
						resizedImage = image;
					} else {
						resizedImage = new BufferedImage((int) targetWidth, (int) targetHeight, BufferedImage.TYPE_INT_RGB);

						Graphics2D g = resizedImage.createGraphics();
						g.setComposite(AlphaComposite.Src);
						g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
						g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
						g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
						g.drawImage(image, 0, 0, (int) targetWidth, (int) targetHeight, null);
						g.dispose();
					}

					String filename = Integer.toString(i) + "." + format;
					ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
					ImageWriteParam param = writer.getDefaultWriteParam();
					param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
					param.setCompressionQuality(quality);
					Path out = tmp.resolve(filename);
					log.info("Writing to: filename={}", out);
					writer.setOutput(ImageIO.createImageOutputStream(out.toFile()));
					writer.write(null, new IIOImage(resizedImage, null, null), param);
					output.add(out);
				}
			}

			return new CloseableIterator<Path>()
			{
				Iterator<Path> sub = output.iterator();

				@Override
				public void close() throws IOException
				{
					FileUtils.deleteDirectory(tmp.toFile());
				}

				@Override
				public boolean hasNext()
				{
					return sub.hasNext();
				}

				@Override
				public Path next()
				{
					return sub.next();
				}
			};
		}
		catch (Throwable ex) {
			FileUtils.deleteDirectory(tmp.toFile());
			throw ex;
		}
	}

	@Override
	public void close()
	{
	}
}
