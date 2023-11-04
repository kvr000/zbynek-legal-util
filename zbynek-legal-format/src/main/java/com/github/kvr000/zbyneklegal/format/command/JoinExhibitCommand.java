package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.table.TsvUpdator;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.text.StringSubstitutor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class JoinExhibitCommand extends AbstractCommand
{
	public static final String DEFAULT_EXHIBIT_SWEAR =
			"""
					This is Exhibit "{exhibit}" referred to in the affidavit of
					{name}                    {date}
					sworn (or affirmed) before me on [dd/mmm/yyyy]

					___________________________________________
					A Commissioner for taking Affidavits for {province}""";

	private final ZbynekLegalFormat.Options mainOptions;

	private Options options = new Options();

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
		switch (arg) {
		case "-p":
			options.firstPage = Integer.parseInt(needArgsParam(options.firstPage, args));
			return true;

		case "-e":
			options.exhibitText = needArgsParam(options.exhibitText, args);
			return true;

		case "-s":
			String[] values = needArgsParam(null, args).split("=", 2);
			if (values.length != 2) {
				throw new IllegalArgumentException("Expecting key=value for -s option");
			} else if (options.substitutes.containsKey(values[0])) {
				throw new IllegalArgumentException("Key already specified for -s option: " + values[0]);
			}
			options.substitutes.put(values[0], values[1]);
			return true;

		case "-k":
			options.listFileKey = needArgsParam(options.listFileKey, args);
			return true;

		case "-i":
			options.ignoreMissing = true;
			return true;
		}
		return super.parseOption(context, arg, args);
	}

	@Override
	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		ImmutableList<String> remaining = ImmutableList.copyOf(args);
		if (remaining.isEmpty() == (mainOptions.getListFile() == null)) {
			return usage(context, "Need one or more parameters as source files or -l listfile provided");
		}
		options.inputs = remaining.isEmpty() ? null : remaining;
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}
		if ((options.inputs == null) == (mainOptions.getListFile() == null)) {
			return usage(context, "input files or -l listfile required");
		}
		if ((mainOptions.getListFile() == null) != (options.listFileKey == null)) {
			return usage(context, "none of both listFile and listFileKey should be provided");
		}
		if (options.firstPage == null) {
			options.firstPage = 1;
		}
		if (options.exhibitText == null) {
			options.exhibitText = DEFAULT_EXHIBIT_SWEAR;
		}
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		PDFMergerUtility merger = new PDFMergerUtility();

		Map<String, InputEntry> files;

		if (mainOptions.getListFile() != null) {
			filesIndex = new TsvUpdator(Paths.get(mainOptions.getListFile()), "Name");
			files = readListFile();
		}
		else {
			files = options.inputs.stream()
					.collect(Collectors.toMap(
							Function.identity(),
							file -> InputEntry.builder().filename(file).build(),
							(a, b) -> { throw new IllegalArgumentException("File specified twice: " + a); },
							LinkedHashMap::new
					));
		}

		pageCounter = options.firstPage;
		try (PDDocument doc = new PDDocument()) {
			for (Map.Entry<String, InputEntry> inputMapEntry: files.entrySet()) {
				InputEntry inputEntry = inputMapEntry.getValue();
				File inputFile;
				try {
					inputFile = findFile(inputMapEntry.getKey());
				} catch (IOException ex) {
					if (options.ignoreMissing) {
						log.error(ex);
						continue;
					}
					throw ex;
				}
				try (PDDocument input = Loader.loadPDF(inputFile)) {
					PDPageTree allPages = input.getDocumentCatalog().getPages();

					for (int i = 0; i < allPages.getCount(); i++) {
						PDPage page = allPages.get(i);
						PDRectangle box = page.getMediaBox();
						if ((page.getRotation() == 0 || page.getRotation() == 180) &&
								box.getWidth() > box.getHeight()) {
							page.setRotation(page.getRotation() + 90);
						}
						if (page.getRotation() == 0 || page.getRotation() == 180) {
							inputEntry.width = box.getWidth();
							inputEntry.height = box.getHeight();
						}
						else {
							inputEntry.height = box.getWidth();
							inputEntry.width = box.getHeight();
						}
						try (PDPageContentStream contentStream = new PDPageContentStream(input, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
							if (i == 0) {
								inputEntry.pageNumber = pageCounter;
								renderExhibitId(inputEntry, contentStream, page);
							}
							renderPageNumber(contentStream, page);
						}
					}
					merger.appendDocument(doc, input);
				}
			}

			doc.save(mainOptions.getOutput());
		}

		if (mainOptions.getListFile() != null) {
			updateListFile(files);
		}

		for (InputEntry entry: files.values()) {
			log.info("Added entry: name={} exhibit={} page={} width={} height={}",
					entry.filename,
					entry.exhibitId,
					entry.pageNumber,
					entry.width, entry.height
			);
		}

		files.values().stream()
				.filter(entry -> entry.pageNumber < 0)
				.forEach(entry -> log.error("File missing: {}", entry.filename));
		log.info("Written output in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	private Map<String, InputEntry> readListFile() throws IOException
	{
			if (filesIndex.getHeaders().get(options.listFileKey + " Exh") == null) {
				throw new IllegalArgumentException("Key not found in index file: " + options.listFileKey + " Exh");
			}
			return filesIndex.listEntries().entrySet().stream()
					.filter(rec -> !Strings.isNullOrEmpty(rec.getValue().get(options.listFileKey + " Exh")))
					.collect(ImmutableMap.toImmutableMap(
							Map.Entry::getKey,
							rec -> {
								try {
									return InputEntry.builder()
											.filename(rec.getKey())
											.swornPosition(Optional.ofNullable(rec.getValue().get("Sworn Pos"))
													.map(Strings::emptyToNull)
													.map(pos -> {
														String[] split = pos.split(";", 2);
														if (split.length != 2) {
															throw new IllegalArgumentException("Sworn Pos must contain two semicolon separated numbers, got: " + pos);
														}
														return new float[]{Float.parseFloat(split[0]), Float.parseFloat(split[1])};
													})
													.orElse(null)
											)
											.build();
								}
								catch (Exception ex) {
									throw new IllegalArgumentException("Failed to process entry file=" + rec.getKey() + ": " + ex, ex);
								}
							}
					));
	}

	private void updateListFile(Map<String, InputEntry> files) throws IOException
	{
		files.values().stream()
				.forEach(entry -> {
					filesIndex.setValue(entry.filename, options.listFileKey + " Pg", Integer.toString(entry.pageNumber));
					filesIndex.setValue(entry.filename, options.listFileKey + " Exh", entry.exhibitId);
				});
		filesIndex.save();
	}

	private File findFile(String name) throws IOException
	{
		File out;
		if ((out = new File(name)).exists()) {
			return out;
		}
		if ((out = new File(name + ".pdf")).exists()) {
			return out;
		}
		throw new FileNotFoundException("File not found: " + name);
	}

	private void renderPageNumber(PDPageContentStream contentStream, PDPage page) throws IOException
	{
		String message = String.format("Pg %03d", pageCounter++);

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
				pageCounter - 1,
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


	private void renderExhibitId(InputEntry entry, PDPageContentStream contentStream, PDPage page) throws IOException
	{
		entry.exhibitId = String.format("%c%c", exhibitCounter/26 + 'A', exhibitCounter%26 + 'A');
		String message = StringSubstitutor.replace(
				options.exhibitText,
				ImmutableMap.<String, String>builder()
						.putAll(options.substitutes)
						.put("exhibit", entry.exhibitId)
						.build(),
				"{",
				"}"
		);

		++exhibitCounter;

		PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		float fontSize = 12.0f;

		PDRectangle pageSize = page.getMediaBox();
		int lineCount = message.split("\n").length;
		float stringWidth = Stream.of(message.split("\n"))
				.map(s -> {
					try {
						return font.getStringWidth(s) * fontSize / 1000f;
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				})
				.reduce(0.0f, Float::max);
		float stringHeight = font.getBoundingBox().getHeight() * fontSize / 1000f;

		int rotation = page.getRotation();
		boolean rotate = rotation == 90 || rotation == 270;
		float pageWidth = rotate ? pageSize.getHeight() : pageSize.getWidth();
		float pageHeight = rotate ? pageSize.getWidth() : pageSize.getHeight();

		float xPosition;
		float yPosition;
		if (entry.swornPosition != null) {
			xPosition = rotate ? entry.swornPosition[1] : entry.swornPosition[0];
			yPosition = rotate ? entry.swornPosition[0] : entry.swornPosition[1];
		}
		else {
			xPosition = rotate ? (10) : (10);
			yPosition = rotate ? (10) : (pageHeight - stringHeight - 10);
		}
		log.info("Exhibit: page={} sw={} sh={} width={} height={} x={} y={}\n",
				pageCounter - 1,
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
		renderMultiLine(contentStream, page, xPosition, yPosition, stringHeight, message);
		contentStream.endText();
	}

	private void renderMultiLine(PDPageContentStream contentStream, PDPage page, float x, float y, float height, String message) throws IOException {
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

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"inputs...", "files to copy"
		);
	}

	private TsvUpdator filesIndex;

	private int pageCounter;

	private int exhibitCounter;

	public static class Options
	{
		private List<String> inputs;

		private String listFileKey = null;

		private Integer firstPage = null;

		private String exhibitText = null;

		private boolean ignoreMissing = false;

		private final Map<String, String> substitutes = new LinkedHashMap<>();
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
