package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.format.SizeFormat;
import com.github.kvr000.zbyneklegal.format.pdf.DocumentWrapper;
import com.github.kvr000.zbyneklegal.format.pdf.PdfRenderer;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CountingOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.base.lang.MultiCloseable;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PdfSplitCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

	private final Options options = new Options();

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
		switch (arg) {
		case "-s":
			options.maxSize = SizeFormat.parseSize(needArgsParam(options.maxSize, args));
			return true;

		case "-p":
			options.maxPages = Integer.parseInt(needArgsParam(options.maxPages, args));
			return true;

		case "-g":
			options.pairing = Integer.parseInt(needArgsParam(options.pairing, args));
			return true;

		}
		return super.parseOption(context, arg, args);
	}

	@Override
	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		ImmutableList<String> remaining = ImmutableList.copyOf(args);
		if (remaining.isEmpty()) {
			return usage(context, "Need one or more parameters as source files");
		}
		options.inputs = remaining;
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}

		if (options.pairing == null) {
			options.pairing = 2;
		}
		if (options.maxSize == null) {
			options.maxSize = Long.MAX_VALUE;
		}
		if (options.maxPages == null) {
			options.maxPages = Integer.MAX_VALUE;
		}

		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		try (MultiCloseable<IOException> resources = new MultiCloseable<>()) {
			TreeMap<Integer, DocumentWrapper> pageToFile = new TreeMap<>();
			int totalPages = 0;
			for (String inputFile : options.inputs) {
				DocumentWrapper input = new DocumentWrapper(new File(inputFile));
				resources.add(input);
				pageToFile.put(totalPages, input);
				totalPages += input.getDocument().getNumberOfPages();
			}
			int currentOutput = 0;
			for (int currentStart = 0; currentStart < totalPages; ) {
				PDFMergerUtility merger = new PDFMergerUtility();
				int lowEnd = Math.min(currentStart + options.pairing, totalPages);
				int highEnd = Math.min((currentStart + options.maxPages >= 0) ? (currentStart + options.maxPages) : Integer.MAX_VALUE, totalPages);

				for (; ; ) {
					int middle = ((lowEnd + highEnd) / 2) / options.pairing * options.pairing;
					if (middle <= lowEnd) {
						middle = highEnd;
					}
					try (PDDocument doc = new PDDocument(); PdfRenderer renderer = new PdfRenderer(doc)) {
						for (int i = currentStart; i < middle; ) {
							Map.Entry<Integer, DocumentWrapper> entry = pageToFile.floorEntry(currentStart + i);
							if (entry.getKey() - entry.getValue().getDocument().getNumberOfPages() == i && entry.getKey() < middle) {
								merger.appendDocument(doc, entry.getValue().getFreshDocument());
								i = entry.getKey();
							} else {
								List<PDPage> pages = renderer.cloneSourcePages(entry.getValue());
								for (int end = Math.min(middle, pages.size() - entry.getKey()); i < end; ++i) {
									PDPage page = pages.get(i - entry.getKey());
									doc.addPage(page);
								}
							}
						}

						CountingOutputStream output = new CountingOutputStream(OutputStream.nullOutputStream());
						doc.save(output);
						if (output.getCount() > options.maxSize && middle - options.pairing > currentStart) {
							highEnd = middle - options.pairing;
						} else if (middle < highEnd && middle > lowEnd) {
							lowEnd = middle;
						} else {
							String filename = FilenameUtils.removeExtension(mainOptions.getOutput()) + String.format("-%04d.", currentOutput) + FilenameUtils.getExtension(mainOptions.getOutput());
							doc.save(new File(filename));
							log.info("Saved output: file={} size={} pages={}", filename, Files.size(Paths.get(filename)), doc.getNumberOfPages());
							++currentOutput;
							currentStart = middle;
							break;
						}
					}
				}
			}
		}

		log.info("Processed in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.of(
				"-s max-size[BKMG]", "max file size",
				"-p page-count", "max number of pages",
				"-g group-size", "number of pages to group together (default is 2)"
		);
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"inputs...", "files to merge and split"
		);
	}

	public static class Options
	{
		private Long maxSize = null;

		private Integer maxPages = null;

		private Integer pairing = null;

		List<String> inputs;
	}
}
