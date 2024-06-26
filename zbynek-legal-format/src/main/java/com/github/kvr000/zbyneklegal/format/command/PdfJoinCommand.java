package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.pdf.PdfFiles;
import com.github.kvr000.zbyneklegal.format.pdf.PdfRenderer;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PdfJoinCommand extends AbstractCommand
{
	private final PdfFiles pdfFiles;

	private final ZbynekLegalFormat.Options mainOptions;

	private final Options options = new Options();

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
			case "--decompress" -> {
				options.decompress = true;
				return true;
			}
			case "--append" -> {
				options.append = true;
				return true;
			}
			case "--skip-first" -> {
				options.skipFirst = true;
				return true;
			}
			case "-a" -> {
				options.firstPage = Integer.parseInt(needArgsParam(options.firstPage, args));
				return true;
			}
			case "-p" -> {
				String[] pos = needArgsParam(options.pagePosition, args).split(",", 2);
				if (pos.length != 2) {
					throw new IllegalArgumentException("-p parameter requires x,y format (both from interval 0 - 1");
				}
				options.pagePosition =  new float[]{ Float.parseFloat(pos[0]), Float.parseFloat(pos[1]) };
				return true;
			}
			case "-f" -> {
				options.pagePattern = needArgsParam(options.pagePattern, args);
				String.format(options.pagePattern, 1);
				return true;
			}
		}
		return super.parseOption(context, arg, args);
	}

	@Override
	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		options.inputs = ImmutableList.copyOf(args);
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}

		if (options.inputs.isEmpty()) {
			return usage(context, "inputs are mandatory");
		}

		if (options.pagePattern == null) {
			options.pagePattern = "Pg %03d";
		}

		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		try (
			PDDocument doc = options.append ? pdfFiles.load(Paths.get(mainOptions.getOutput())) : new PDDocument();
			PdfRenderer renderer = new PdfRenderer(doc)
		) {
			int internalPageCounter = doc.getNumberOfPages();
			int inputCounter = 0;
			PDFMergerUtility merger = new PDFMergerUtility();
			for (String inputName: options.inputs) {
				try (PDDocument input = pdfFiles.load(Paths.get(inputName))) {
					if (options.decompress) {
						pdfFiles.decompress(input);
					}
					merger.appendDocument(doc, input);
					if (options.firstPage != null) {
						for (; internalPageCounter < doc.getNumberOfPages(); ++internalPageCounter) {
							if (inputCounter == 0 && options.skipFirst) {
								continue;
							}
							PDPage page = doc.getPage(internalPageCounter);
							try (PDPageContentStream contentStream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
								renderer.renderPageNumberAt(contentStream, page, options.pagePosition, options.pagePattern, options.firstPage + internalPageCounter);
							}
						}
					}
				}
				++inputCounter;
			}
			pdfFiles.saveViaTmp(doc, Paths.get(mainOptions.getOutput()));
		}

		log.info("Processed in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"--decompress", "decompress input files",
			"--append", "append to output",
			"--skip-first", "do not modify first file (typically when appending)",
			"-a start-page", "add page numbers, starting with this parameter value",
			"-p x,y", "position to render page number to (range 0 - 1)",
			"-f page-number-pattern", "page number pattern, such as Page %02d"
		);
	}

	public static class Options
	{
		boolean decompress;

		boolean append;

		boolean skipFirst;

		List<String> inputs;

		Integer firstPage;

		float[] pagePosition;

		String pagePattern;
	}
}
