package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.pdf.DocumentWrapper;
import com.github.kvr000.zbyneklegal.format.pdf.PdfFiles;
import com.github.kvr000.zbyneklegal.format.pdf.PdfRenderer;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PdfReplaceCommand extends AbstractCommand
{
	private final PdfFiles pdfFiles;

	private final ZbynekLegalFormat.Options mainOptions;

	private final Options options = new Options();

	@Override
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
			case "-a" -> {
				String addCommand = needArgsParam(null, args);
				options.operations.add(Pair.of(Integer.parseInt(addCommand), null));
				return true;
			}
			case "--replace-meta" -> {
				options.replaceMeta = true;
				return true;
			}
			case "--meta-from" -> {
				options.metaFrom = needArgsParam(options.metaFrom, args);
				return true;
			}
			case "--title" -> {
				options.meta.put(COSName.TITLE.getName(), needArgsParam(options.meta.get(COSName.TITLE.getName()), args));
				return true;
			}
			case "--author" -> {
				options.meta.put(COSName.AUTHOR.getName(), needArgsParam(options.meta.get(COSName.AUTHOR.getName()), args));
				return true;
			}
			case "--subject" -> {
				options.meta.put(COSName.SUBJECT.getName(), needArgsParam(options.meta.get(COSName.SUBJECT.getName()), args));
				return true;
			}
			case "--delete-all-meta" -> {
				options.deleteAllMeta = true;
				options.replaceMeta = true;
				return true;
			}
			case "--delete-meta" -> {
				options.meta.put(needArgsParam(null, args), null);
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

		if (options.inputFile == null && (options.meta.isEmpty() && options.metaFrom == null)) {
			return usage(context, "-i input option or meta setting options are mandatory");
		}
		if (options.operations.stream().anyMatch(e -> e.getValue() != null) && options.inputFile == null) {
			return usage(context, "-i input option is mandatory if operations are specified");
		}

		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		try (PDDocument doc = new PDDocument(); PdfRenderer renderer = new PdfRenderer(doc)) {
			PDFMergerUtility merger = new PDFMergerUtility();
			try (PDDocument base = Loader.loadPDF(new File(mainOptions.getOutput()))) {
				merger.appendDocument(doc, base);
				Map<String, String> meta = new LinkedHashMap<>();
				if (!options.replaceMeta) {
					base.getDocumentInformation().getMetadataKeys().forEach(key ->
						meta.put(key, base.getDocumentInformation().getCustomMetadataValue(key)));
				}
				if (options.inputFile != null) {
					try (DocumentWrapper input = new DocumentWrapper(new File(options.inputFile))) {
						List<PDPage> inputPages = renderer.cloneSourcePages(input);

						for (Pair<Integer, Integer> operation : options.operations) {
							if (operation.getRight() == null) {
								renderer.insertBlankPage(operation.getLeft());
							}
							else {
								renderer.replacePage(operation.getLeft() - 1, inputPages.get(operation.getRight() - 1));
							}
						}
					}
				}
				else {
					for (Pair<Integer, Integer> operation : options.operations) {
						if (operation.getRight() == null) {
							renderer.insertBlankPage(operation.getLeft());
						}
					}
				}
				if (options.deleteAllMeta) {
					meta.clear();
				}
				for (String key: doc.getDocumentInformation().getMetadataKeys()) {
					doc.getDocumentInformation().setCustomMetadataValue(key, null);
				}
				if (options.metaFrom != null) {
					try (PDDocument metaPdf = Loader.loadPDF(new File(options.metaFrom))) {
						for (String key: metaPdf.getDocumentInformation().getMetadataKeys()) {
							meta.put(key, metaPdf.getDocumentInformation().getCustomMetadataValue(key));
						}
					}
				}
				meta.putAll(options.meta);
				for (Map.Entry<String, String> metaEntry: meta.entrySet()) {
					doc.getDocumentInformation().setCustomMetadataValue(metaEntry.getKey(), metaEntry.getValue());
				}
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
				"-i input-file", "input file for operation",
				"-m destinationPage[=sourcePage]", "moves sourcePage to destinationPage (same page if sourcePage not provided)",
				"-a destinationPage", "adds new blank page",
				"--title document-title", "sets document title",
				"--subject document-subject", "sets document subject",
				"--author document-author", "sets document author",
				"--replace-meta", "replace meta to original document (default is to keep)",
				"--meta-from document", "copy all meta from specified document",
				"--delete-all-meta", "delete all meta fields",
				"--delete-meta key", "delete meta field named key"
		);
	}

	public static class Options
	{
		String inputFile;

		boolean deleteAllMeta;

		boolean replaceMeta;

		String metaFrom;

		Map<String, String> meta = new LinkedHashMap<>();

		List<Pair<Integer, Integer>> operations = new ArrayList<>();
	}
}
