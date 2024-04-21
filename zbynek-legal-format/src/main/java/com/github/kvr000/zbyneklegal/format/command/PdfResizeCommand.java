package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.collection.CloseableIterator;
import com.github.kvr000.zbyneklegal.format.image.ColorExtractor;
import com.github.kvr000.zbyneklegal.format.pdf.PdfRenderer;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PdfResizeCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

	private final ColorExtractor colorExtractor;

	private Options options = new Options();

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
		switch (arg) {
			case "-w" -> {
				options.targetWidth = Integer.parseInt(needArgsParam(options.targetWidth, args));
				return true;
			}
			case "-s" -> {
				options.scale = Double.parseDouble(needArgsParam(options.scale, args));
				return true;
			}
			case "-q" -> {
				options.quality = Float.parseFloat(needArgsParam(options.quality, args));
				return true;
			}
		}
		return super.parseOption(context, arg, args);
	}

	@Override
	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		options.input = ImmutableList.copyOf(args);
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}
		if (options.input.isEmpty() || options.input.stream().anyMatch(s -> s.equals(mainOptions.getOutput()))) {
			return usage(context, "input is mandatory and must be different from output");
		}

//		if ((options.scale == null) == (options.targetWidth == null)) {
//			return usage(context, "Exactly one of -s scale and -w target-width must be specified");
//		}
//		if (options.scale == null) {
//			return usage(context, "As of now, only -s is supported");
//		}

		if (options.quality == null) {
			options.quality = 0.8f;
		}

		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		try (PDDocument output = new PDDocument()) {
			for (String input : options.input) {
				mergeResizedDocument(output, input);
			}
			output.save(new File(mainOptions.getOutput()));
		}

		log.info("Processed in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	private void mergeResizedDocument(PDDocument doc, String inputName) throws IOException
	{
		try (
			PDDocument input = Loader.loadPDF(new File(inputName));
			PdfRenderer renderer = new PdfRenderer(input);
			CloseableIterator<Path> images = renderer.renderImages(Paths.get(inputName), "jpeg", options.scale, options.quality)
		) {
			if (doc.getDocumentInformation().getMetadataKeys().isEmpty()) {
				for (String key: input.getDocumentInformation().getMetadataKeys()) {
					doc.getDocumentInformation().setCustomMetadataValue(key, input.getDocumentInformation().getCustomMetadataValue(key));
				}
			}
			for (int i = 0; images.hasNext(); ++i) {
				PDPage inputPage = input.getPage(i);
				PDRectangle mediaBox = inputPage.getMediaBox();
				if (inputPage.getRotation() == 90 || inputPage.getRotation() == 270) {
					mediaBox = new PDRectangle(mediaBox.getLowerLeftY(), mediaBox.getLowerLeftX(), mediaBox.getHeight(), mediaBox.getWidth());
				}
				PDPage outputPage = new PDPage(mediaBox);

				PDImageXObject ximage = PDImageXObject.createFromFile(images.next().toString(), doc);
				try (PDPageContentStream contentStream = new PDPageContentStream(doc, outputPage)) {
					contentStream.drawImage(ximage, mediaBox.getLowerLeftX(), mediaBox.getLowerLeftY(), mediaBox.getWidth(), mediaBox.getHeight());
				}

				doc.addPage(outputPage);
			}
		}
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.of(
				"-w target-width", "target page width (or height / shorter size if rotated)",
				"-s scale", "page scale factor",
				"-q quality", "compression quality"
		);
	}

	@Override
	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"input-file...", "input files to be concatenated and resized"
		);
	}

	public static class Options
	{
		List<String> input;

		Integer targetWidth;

		Double scale;

		Float quality;
	}
}
