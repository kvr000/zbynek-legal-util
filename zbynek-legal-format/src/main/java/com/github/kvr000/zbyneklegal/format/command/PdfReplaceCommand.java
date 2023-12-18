package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.pdf.DocumentWrapper;
import com.github.kvr000.zbyneklegal.format.pdf.PdfRenderer;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PdfReplaceCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

	private Options options = new Options();

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
		switch (arg) {
			case "-i" -> {
				options.inputFile = needArgsParam(options.inputFile, args);
				return true;
			}
			case "-m" -> {
				String[] moveCommand = needArgsParam(null, args).split("=", 2);
				if (moveCommand.length == 1) {
					options.operations.add(Pair.of(Integer.parseInt(moveCommand[0]), Integer.parseInt(moveCommand[0])));
				}
				else {
					options.operations.add(Pair.of(Integer.parseInt(moveCommand[0]), Integer.parseInt(moveCommand[1])));
				}
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

		if (options.inputFile == null) {
			return usage(context, "-i input option is mandatory");
		}

		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		File tmp;
		try (PDDocument doc = new PDDocument(); PdfRenderer renderer = new PdfRenderer(doc)) {
			PDFMergerUtility merger = new PDFMergerUtility();
			try (PDDocument base = Loader.loadPDF(new File(mainOptions.getOutput()))) {
				try (DocumentWrapper input = new DocumentWrapper(new File(options.inputFile))) {
					merger.appendDocument(doc, base);
					List<PDPage> inputPages = renderer.cloneSourcePages(input);

					for (Pair<Integer, Integer> move : options.operations) {
						renderer.replacePage(move.getLeft()-1, inputPages.get(move.getRight()-1));
					}

					tmp = File.createTempFile("tmp", ".pdf");
					tmp.deleteOnExit();
					doc.save(tmp);
				}
			}
		}
		Files.copy(tmp.toPath(), Paths.get(mainOptions.getOutput()), StandardCopyOption.REPLACE_EXISTING);
		Files.delete(tmp.toPath());

		log.info("Processed in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.of(
				"-i input-file", "input file for operation",
				"-m destinationPage[=sourcePage]", "moves sourcePage to destinationPage (same page if sourcePage not provided)"
		);
	}

	public static class Options
	{
		String inputFile;

		List<Pair<Integer, Integer>> operations = new ArrayList<>();
	}
}
