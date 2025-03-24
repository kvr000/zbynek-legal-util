package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.pdf.PdfFiles;
import com.github.kvr000.zbyneklegal.format.pdf.PdfRenderer;
import com.github.kvr000.zbyneklegal.format.table.TsvUtil;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

import javax.inject.Inject;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
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
			case "--align" -> {
				options.align = Integer.parseInt(needArgsParam(options.align == 0 ? null : options.align, args));
				if (options.align < 0) {
					throw new IllegalArgumentException("align must not be negative");
				}
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

		Map<String, Pair<Integer, Integer>> inputs = new LinkedHashMap<>();
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
					int start = doc.getNumberOfPages();
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
					if (options.align > 1) {
						while (doc.getNumberOfPages() % options.align != 0) {
							renderer.insertBlankPage(doc.getNumberOfPages());
						}
					}
					int size = doc.getNumberOfPages() - start;
					inputs.put(inputName, Pair.of(start, size));
				}
				++inputCounter;
			}
			pdfFiles.saveViaTmp(doc, Paths.get(mainOptions.getOutput()));
		}

		log.info("Processed in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));

		inputs.forEach((file, range) -> {
			System.out.println(TsvUtil.formatTsv(file, range.getLeft() + 1, range.getRight()));
		});

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
			"-f page-number-pattern", "page number pattern, such as Page %02d",
			"--align number-of-pages", "align documents to number of pages"
		);
	}

	public static class Options
	{
		boolean decompress;

		boolean append;

		boolean skipFirst;

		int align;

		List<String> inputs;

		Integer firstPage;

		float[] pagePosition;

		String pagePattern;
	}
}
