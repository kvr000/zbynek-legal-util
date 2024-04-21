package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.collection.CloseableIterator;
import com.github.kvr000.zbyneklegal.format.image.ColorExtractor;
import com.github.kvr000.zbyneklegal.format.pdf.PdfRenderer;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import lombok.extern.log4j.Log4j2;
import net.dryuf.base.function.ThrowingFunction;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;


@Log4j2
public class MergeInkCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

	private final ColorExtractor colorExtractor;

	private final byte[] lowBlueScalar;

	private final byte[] highBlueScalar;

	private Options options = new Options();

	@Inject
	public MergeInkCommand(ColorExtractor colorExtractor, ZbynekLegalFormat.Options mainOptions)
	{
		this.colorExtractor = colorExtractor;
		this.mainOptions = mainOptions;

		lowBlueScalar = colorExtractor.hsvToBytes(140, 0.15f, 0.15f);
		highBlueScalar = colorExtractor.hsvToBytes(320, 1.0f, 1.0f);
	}

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
		switch (arg) {
			case "-b" -> {
				options.baseFile = needArgsParam(options.baseFile, args);
				return true;
			}
			case "-i" -> {
				options.inkFile = needArgsParam(options.inkFile, args);
				return true;
			}
			case "-f" -> {
				options.flipTill = Integer.parseInt(needArgsParam(options.flipTill, args));
				return true;
			}
		}
		return super.parseOption(context, arg, args);
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}

		if (options.baseFile == null) {
			return usage(context, "-b base-file option is mandatory");
		}
		if (options.inkFile == null) {
			return usage(context, "-i ink-file option is mandatory");
		}
		if (options.flipTill == null) {
			options.flipTill = Integer.MAX_VALUE;
		}

		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		try (
			PDDocument doc = Loader.loadPDF(new File(options.baseFile));
			PdfRenderer renderer = new PdfRenderer(doc);
			PDDocument inkFile = Loader.loadPDF(new File(options.inkFile));
			CloseableIterator<Path> images = renderer.renderImages(Paths.get(options.inkFile), "png", null, 0.5f);
		) {
			for (int i = 0; i < inkFile.getNumberOfPages(); ++i) {
				Path imagePath = images.next();
				int pageI = i;
				PDPage docPage = doc.getPage(pageI);
				PDRectangle box = docPage.getMediaBox();
				Pair<byte[], Matrix> result = ((Callable<Pair<byte[], Matrix>>) () -> {
					byte[] extracted = colorExtractor.extractColor(Files.readAllBytes(imagePath), lowBlueScalar, highBlueScalar);
					Matrix transformation;
					if (renderer.isRotated(docPage)) {
						if (pageI < options.flipTill) {
							transformation = Matrix.getScaleInstance(-box.getWidth(), box.getHeight());
							transformation.concatenate(Matrix.getTranslateInstance(-1, 0));
						} else {
							transformation = Matrix.getScaleInstance(box.getWidth(), box.getHeight());
						}
						transformation.concatenate(Matrix.getRotateInstance(Math.PI / 2, 1, 0));
					} else {
						if (pageI < options.flipTill) {
							transformation = Matrix.getScaleInstance(box.getWidth(), -box.getHeight());
							transformation.concatenate(Matrix.getTranslateInstance(0, -1));
						} else {
							transformation = Matrix.getScaleInstance(box.getWidth(), box.getHeight());
						}
					}
					//Files.write(Paths.get(String.format("page-%03d-ink.png", pageI)), extracted);
					return Pair.of(extracted, transformation);
				}).call();
				((ThrowingFunction<Pair<byte[], Matrix>, Void, Exception>) (params) -> {
					PDImageXObject pdfExtracted = PDImageXObject.createFromByteArray(doc, params.getLeft(), String.format("ink-image-%03d.png", pageI));
					try (PDPageContentStream stream = new PDPageContentStream(doc, docPage, PDPageContentStream.AppendMode.APPEND, true)) {
						stream.drawImage(pdfExtracted, params.getRight());
					}
					return null;
				}).apply(result);
			}
			doc.save(mainOptions.getOutput());
		}

		log.info("Processed in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.of(
				"-b base-file", "original digital file",
				"-i ink-file", "printed file with ink text to copy",
				"-f flip-till", "flip source image vertically till this page"
		);
	}

	public static class Options
	{
		String baseFile;

		String inkFile;

		Integer flipTill;
	}
}
