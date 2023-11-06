package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.pdf.PdfRenderer;
import com.github.kvr000.zbyneklegal.format.table.TsvUpdator;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class AddPageNumbersCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

	private Options options = new Options();

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
		switch (arg) {
		case "-a":
			options.relativePage = Integer.parseInt(needArgsParam(options.relativePage, args));
			return true;

		case "-p":
			options.markPages.addAll(Arrays.stream(needArgsParam(null, args).split(","))
					.flatMap(s -> {
						if (s.indexOf('-') >= 0) {
							String[] range = s.split("-", 2);
							return IntStream.rangeClosed(Integer.parseInt(range[0]), Integer.parseInt(range[1])).boxed();
						}
						else {
							return Stream.of(Integer.parseInt(s));
						}
					})
					.toList()
			);
			return true;

			case "-f":
				options.markFiles.addAll(Arrays.stream(needArgsParam(null, args).split(","))
						.flatMap(s -> {
							if (s.indexOf('-') >= 0) {
								String[] range = s.split("-", 2);
								return IntStream.rangeClosed(Integer.parseInt(range[0]), Integer.parseInt(range[1])).boxed();
							}
							else {
								return Stream.of(Integer.parseInt(s));
							}
						})
						.toList()
				);
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
		if (options.relativePage == null) {
			options.relativePage = 1;
		}
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		PDFMergerUtility merger = new PDFMergerUtility();

		pageCounter = options.relativePage;
		try (PDDocument doc = new PDDocument()) {
			int fileCounter = 1;
			for (String inputName: options.inputs) {
				File inputFile = new File(inputName);
				try (PDDocument input = Loader.loadPDF(inputFile); PdfRenderer renderer = new PdfRenderer(input)) {
					PDPageTree allPages = input.getDocumentCatalog().getPages();

					for (int i = 0; i < allPages.getCount(); i++) {
						PDPage page = allPages.get(i);
						PDRectangle box = page.getMediaBox();
						if ((page.getRotation() == 0 || page.getRotation() == 180) &&
								box.getWidth() > box.getHeight()) {
							page.setRotation(page.getRotation() + 90);
						}
						if ((options.markFiles.isEmpty() && options.markPages.isEmpty()) || options.markFiles.contains(fileCounter) || options.markPages.contains(pageCounter)) {
							try (PDPageContentStream contentStream = new PDPageContentStream(input, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
								renderer.renderPageNumber(contentStream, page, pageCounter++);
							}
						}
						++pageCounter;
					}
					merger.appendDocument(doc, input);
				}
				++fileCounter;
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
				"-a page-number", "first page number (default 1)",
				"-p page-page,page,page-page", "list of pages to mark with page number, separated by commas, or ranges of separated by dash",
				"-f file-file,file,file-file", "list of files to mark with page numbers, separated by commas, or ranges of separated by dash"
		);
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"inputs...", "files to merge and mark with page numbers"
		);
	}

	private TsvUpdator filesIndex;

	private int pageCounter;

	private int exhibitCounter;

	public static class Options
	{
		private Integer relativePage = null;

		private Set<Integer> markPages = new HashSet<>();

		private Set<Integer> markFiles = new HashSet<>();

		private List<String> inputs = new ArrayList<>();
	}

	@Builder
	static class InputEntry
	{
		public final String filename;
		float[] swornPosition;
		@Builder.Default
		int pageNumber = -1;
		String exhibitId;

		float width, height;
	}
}
