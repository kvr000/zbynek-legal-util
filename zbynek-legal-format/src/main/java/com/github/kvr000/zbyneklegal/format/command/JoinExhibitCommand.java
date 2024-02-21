package com.github.kvr000.zbyneklegal.format.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.indexfile.IndexReader;
import com.github.kvr000.zbyneklegal.format.pdf.PdfRenderer;
import com.github.kvr000.zbyneklegal.format.table.TableUpdator;
import com.github.kvr000.zbyneklegal.format.table.TableUpdatorFactory;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.base.function.ThrowingBiConsumer;
import net.dryuf.base.function.ThrowingRunnable;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class JoinExhibitCommand extends AbstractCommand
{
	public static final Pattern DATE_WITH_SUFFIX_PATTERN = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})-.*$");

	DateTimeFormatter SWORN_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MMM/dd", Locale.ROOT);

	public static final String DEFAULT_EXHIBIT_SWEAR_AFFIRM =
			"""
					This is Exhibit "{exhibit}" referred to in the affidavit of
					{name}                    {date}
					sworn (or affirmed) before me on [dd/mmm/yyyy]

					___________________________________________
					A Commissioner for taking Affidavits for {province}""";

	public static final String EXHIBIT_SWEAR =
			"""
					This is Exhibit "{exhibit}" referred to in the affidavit of
					{name}                    {date}
					sworn before me on [dd/mmm/yyyy]

					___________________________________________
					A Commissioner for taking Affidavits for {province}""";

	public static final String EXHIBIT_AFFIRM =
			"""
					This is Exhibit "{exhibit}" referred to in the affidavit of
					{name}                    {date}
					affirmed before me on [dd/mmm/yyyy]

					___________________________________________
					A Commissioner for taking Affidavits for {province}""";

	private static final ObjectMapper jsonMapper = new ObjectMapper();

	private final TableUpdatorFactory tableUpdatorFactory;

	private final ZbynekLegalFormat.Options mainOptions;

	private Options options = new Options();

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
		switch (arg) {
		case "--code":
			if (args.hasNext()) {
				options.code = needArgsParam(options.code, args);
			}
			else {
				options.code = "";
			}
			return true;

		case "--extract":
			options.extract.add(needArgsParam(null, args));
			switch (options.extract.get(options.extract.size()-1)) {
			case "first":
			case "last":
			case "exhibit-first":
			case "single":
			case "pair-even":
				break;

			default:
				throw new IllegalArgumentException("Invalid value, allowed: first last exhibit-first single pair-even");
			}
			return true;

		case "--base":
			options.base = needArgsParam(options.base, args);
			return true;

		case "-a":
			options.firstPage = Integer.parseInt(needArgsParam(options.firstPage, args));
			return true;

		case "-e":
			options.firstExhibit = parseExhibitId(needArgsParam(options.firstExhibit, args));
			return true;

		case "-s":
			options.swornText = needArgsParam(options.swornText, args);
			return true;

		case "--sa":
			Preconditions.checkArgument(options.swornText == null, "sworn/affirmed text already provided");
			options.swornText = EXHIBIT_AFFIRM;
			return true;

		case "--ss":
			Preconditions.checkArgument(options.swornText == null, "sworn/affirmed text already provided");
			options.swornText = EXHIBIT_SWEAR;
			return true;

		case "-t":
			String[] values = needArgsParam(null, args).split("=", 2);
			if (values.length != 2) {
				throw new IllegalArgumentException("Expecting key=value for -s option");
			} else if (options.substitutes.containsKey(values[0])) {
				throw new IllegalArgumentException("Key already specified for -s option: " + values[0]);
			}
			options.substitutes.put(values[0], values[1]);
			return true;

		case "--tt":
			Preconditions.checkArgument(options.substituteSource == null, "substitute source already provided");
			options.substituteSource = this::readSubstitutesTable;
			return true;

		case "--ta":
			Preconditions.checkArgument(options.substituteSource == null, "substitute source already provided");
			options.substituteSource = this::readSubstitutesTableAndDate;
			return true;

		case "--tn":
			Preconditions.checkArgument(options.substituteSource == null, "substitute source already provided");
			options.substituteSource = () -> {};
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
		if (options.code != null) {
			return EXIT_CONTINUE;
		}
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
		if (options.code != null) {
			return EXIT_CONTINUE;
		}
		if ((options.inputs == null) == (mainOptions.getListFile() == null)) {
			return usage(context, "input files or -l listfile required");
		}
		if (options.substituteSource == null) {
			options.substituteSource = this::readSubstitutesTableAndDate;
		}
		if ((mainOptions.getListFile() == null) != (mainOptions.getListFileKeys().isEmpty())) {
			return usage(context, "none of both listFile and listFileKey should be provided");
		}
		if (mainOptions.getListFileKeys().size() > 1) {
			log.info("Multiple exhibit sets specified, only the first one will be considered for BASE purposes");
		}
		return EXIT_CONTINUE;
	}

	protected void revalidateOptions() throws Exception
	{
		if (mainOptions.getOutput() == null) {
			throw new IOException("Output file not specified, neither as parameter, nor at index file in Pg section");
		}
		if (options.swornText != null) {
			StringSubstitutor substitutor = new StringSubstitutor(new StringLookup()
			{
				@Override
				public String lookup(String key)
				{
					if (key.equals("exhibit")) {
						return "AA";
					}
					return Optional.ofNullable(options.substitutes.get(key))
						.orElseThrow(() -> new IllegalArgumentException("Template key not found in substitutes: " + key));
				}
			}, "{", "}", '\0');
			substitutor.replace(options.swornText);
		}
	}

	@Override
	public int execute() throws Exception
	{
		if (options.code != null) {
			return executeCode();
		}

		Stopwatch watch = Stopwatch.createStarted();

		PDFMergerUtility merger = new PDFMergerUtility();

		Map<String, InputEntry> files;

		if (mainOptions.getListFile() != null) {
			filesIndex = tableUpdatorFactory.openTableUpdator(Paths.get(mainOptions.getListFile()), "Name");
			indexReader = new IndexReader(filesIndex);
			files = readListFile();
			if (options.firstExhibit == null) {
				options.firstExhibit = Optional.ofNullable(filesIndex.getOptionalValue("BASE", mainOptions.getListFileKeys().get(0) + " Exh"))
						.filter(Predicate.not(Strings::isNullOrEmpty))
						.map(JoinExhibitCommand::parseExhibitId)
						.orElse(null);
			}
			if (options.firstPage == null) {
				options.firstPage = Optional.ofNullable(filesIndex.getOptionalValue("BASE", mainOptions.getListFileKeys().get(0) + " Pg"))
						.filter(Predicate.not(Strings::isNullOrEmpty))
						.map(Integer::parseInt)
						.orElse(null);
			}
			readIndexConfig();
			readIndexIo();
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

		if (options.substituteSource != null) {
			options.substituteSource.run();
		}

		revalidateOptions();

		try (PDDocument doc = options.base == null ? new PDDocument() : Loader.loadPDF(new File(options.base))) {

			basePages = doc.getNumberOfPages();
			internalPageCounter = basePages;
			if (options.firstPage == null) {
				options.firstPage = 1;
			}
			else {
				options.firstPage -= internalPageCounter;
			}
			if (options.firstExhibit == null) {
				options.firstExhibit = 0;
			}
			if (options.swornText == null) {
				options.swornText = DEFAULT_EXHIBIT_SWEAR_AFFIRM;
			}

			exhibitCounter = options.firstExhibit;

			for (Map.Entry<String, InputEntry> inputMapEntry: files.entrySet()) {
				InputEntry inputEntry = inputMapEntry.getValue();
				File inputFile;
				try {
					inputFile = findPdfFile(inputMapEntry.getKey());
				} catch (FileNotFoundException ex) {
					inputEntry.error = ex;
					if (options.ignoreMissing) {
						log.error(ex);
						continue;
					}
					throw ex;
				}
				try (PDDocument input = Loader.loadPDF(inputFile); PdfRenderer renderer = new PdfRenderer(input)) {
					PDPageTree allPages = input.getDocumentCatalog().getPages();

					inputEntry.internalPageNumber = internalPageCounter;
					inputEntry.pageNumber = options.firstPage + internalPageCounter;

					for (int i = 0; i < allPages.getCount(); i++) {
						PDPage page = allPages.get(i);
						if (renderer.rotatedWidth(page) > renderer.rotatedHeight(page)) {
							page.setRotation(page.getRotation() + 90);
						}
						inputEntry.width = renderer.rotatedWidth(page);
						inputEntry.height = renderer.rotatedHeight(page);
						try (PDPageContentStream contentStream = new PDPageContentStream(input, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
							if (i == 0) {
								renderExhibitId(renderer, contentStream, page, inputEntry);
							}
							renderer.renderPageNumber(contentStream, page, options.firstPage + internalPageCounter++);
						}
					}
					merger.appendDocument(doc, input);
				}
				catch (Exception ex) {
					log.error("Failed to process file: {}", inputFile, ex);
					throw ex;
				}
			}

			Map<String, Integer> urlsPages = files.values().stream()
					.filter(entry -> entry.url != null)
					.collect(ImmutableMap.toImmutableMap(
							e -> e.url,
							e -> e.internalPageNumber
					));
			for (int i = 0; i < basePages; ++i) {
				PDPage page = doc.getPage(i);
				List<PDAnnotation> annotations = page.getAnnotations();

				for (PDAnnotation annotation : annotations) {
					if (annotation instanceof PDAnnotationLink link) {
						PDAction action = link.getAction();
						if (action instanceof PDActionURI actionUri) {
							String uri = actionUri.getURI();
							int targetPage = urlsPages.getOrDefault(uri, -1);
							if (targetPage >= 0) {
								PDPageFitDestination newTarget = new PDPageFitDestination();
								newTarget.setPage(doc.getPage(targetPage));
								PDActionGoTo actionPage = new PDActionGoTo();
								actionPage.setDestination(newTarget);
								link.setAction(actionPage);
							}
						}
					}
				}

			}

			if (!options.extract.isEmpty()) {
				boolean extractEven = true;
				Set<Integer> pages = new TreeSet<>(Comparator.reverseOrder());
				for (String command: options.extract) {
					switch (command) {
					case "first":
						if (basePages > 0) {
							pages.add(0);
						}
						break;

					case "last":
						if (basePages > 0) {
							pages.add(basePages - 1);
						}
						break;

					case "exhibit-first":
						files.values().stream().map(e -> e.internalPageNumber).filter(p -> p >= 0).forEach(pages::add);
						break;

					case "single":
						extractEven = false;
						break;

					case "pair-even":
						extractEven = true;
						break;
					}
				}
				int lastIncluded = doc.getNumberOfPages();
				if (extractEven) {
					for (int page : pages) {
						if (lastIncluded > page) {
							for (--lastIncluded; lastIncluded >= ((page + 2) & ~1); --lastIncluded) {
								doc.removePage(lastIncluded);
							}
						}
						lastIncluded = lastIncluded & ~1;
					}
				}
				else {
					for (int page : pages) {
						for (--lastIncluded; lastIncluded > page; --lastIncluded) {
							doc.removePage(lastIncluded);
						}
					}
				}
				for (--lastIncluded; lastIncluded >= 0; --lastIncluded) {
					doc.removePage(lastIncluded);
				}
			}

			doc.save(mainOptions.getOutput());
		}

		if (mainOptions.getListFile() != null) {
			updateListFile(files);
		}

		log.info("Results:");
		for (InputEntry entry: files.values()) {
			if (entry.error != null) {
				if (!(entry.error instanceof FileNotFoundException)) {
					log.error("Error in entry: {}", entry.filename, entry.error);
				}
			}
		}
		for (InputEntry entry: files.values()) {
			if (entry.error != null) {
				if (entry.error instanceof FileNotFoundException) {
					log.error("File not found: {}", entry.filename);
				}
			}
		}
		for (InputEntry entry: files.values()) {
			if (entry.error == null) {
				log.info("Added entry: name={} exhibit={} page={} width={} height={}",
						entry.filename,
						entry.exhibitId,
						entry.pageNumber,
						entry.width, entry.height
				);
			}
		}

		log.info("Exhibit map:\n{}", jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(files.values().stream()
				.filter(e -> e.exhibitId != null)
				.collect(ImmutableMap.toImmutableMap(
						e -> e.filename,
						e -> ImmutableMap.of(
							"id", e.exhibitId,
							"page", e.pageNumber,
							"text", e.exhibitId + " p" + e.pageNumber,
							"url", e.url
						)
				))
		));

		files.values().stream()
				.filter(entry -> entry.pageNumber < 0)
				.forEach(entry -> log.error("File missing: {}", entry.filename));
		log.info("Written output in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	private Map<String, InputEntry> readListFile() throws IOException
	{
		return indexReader.readIndex(mainOptions.getListFileKeys()).entrySet().stream()
					.collect(ImmutableMap.toImmutableMap(
							Map.Entry::getKey,
							rec -> {
								try {
									return InputEntry.builder()
											.filename(rec.getKey())
											.url(filesIndex.getUrl(rec.getKey(), "Name"))
											.swornPosition(Optional.ofNullable(rec.getValue().get("Sworn Pos"))
													.map(Strings::emptyToNull)
													.map(pos -> {
														if (pos.equals("default")) {
															return null;
														}
														String[] split = pos.split(";", 2);
														if (split.length != 2) {
															throw new IllegalArgumentException("Sworn Pos must contain two semicolon separated numbers or be 'default', got: " + pos);
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
					mainOptions.getListFileKeys().forEach((exhibitKey) -> {
						if (indexReader.isExhibitIncluded(entry.filename, Collections.singleton(exhibitKey))) {
							filesIndex.setValue(entry.filename, exhibitKey + " Pg", Integer.toString(entry.pageNumber));
							filesIndex.setValue(entry.filename, exhibitKey + " Exh", Optional.ofNullable(entry.exhibitId).orElse("-"));
						}
					});
				});
		filesIndex.save();
	}

	private void readIndexConfig() throws IOException
	{
		Map<String, Map<String, String>> values;
		try {
			values = filesIndex.readSheet("config", "key");
		}
		catch (FileNotFoundException ex) {
			log.warn("Sheet 'config' not found in provided index file, skipping");
			return;
		}
		for (Map.Entry<String, Map<String, String>> entry: values.entrySet()) {
			String value = Optional.ofNullable(entry.getValue().get("value"))
					.orElseThrow(() -> new IOException("Cannot find column: column=value"));
			switch (entry.getKey()) {
				case "exhibitTemplateName" -> {
					if (options.swornText == null) {
						switch (value) {
							case "swear" -> options.swornText = EXHIBIT_SWEAR;
							case "affirm" -> options.swornText = EXHIBIT_AFFIRM;
							default ->
									throw new IOException("Value for exhibitTemplate unsupported: got=" + value + " supported=swear,affirm");
						}
					}
				}
				case "exhibitTemplateText" -> {
					if (options.swornText == null) {
						options.swornText = value;
					}
				}
				default ->
						throw new IOException("Unknown config option=" + entry.getKey() + " supported=exhibitTemplateName");
			}
		}
	}

	private void readIndexIo() throws IOException
	{
		if (mainOptions.getOutput() == null) {
			if (mainOptions.getListFileKeys().isEmpty()) {
				throw new IOException("No output file provided, nor index key");
			}
			mainOptions.setOutput(
					Optional.ofNullable(filesIndex.getOptionalValue("FILES", mainOptions.getListFileKeys().get(0) + " Pg"))
							.filter(Predicate.not(Strings::isNullOrEmpty))
							.orElse(null)
			);
		}
		if (options.base == null) {
			if (!mainOptions.getListFileKeys().isEmpty()) {
				options.base =
						Optional.ofNullable(filesIndex.getOptionalValue("FILES", mainOptions.getListFileKeys().get(0) + " Exh"))
								.filter(Predicate.not(Strings::isNullOrEmpty))
								.orElse(null);
			}
		}
	}

	private void readSubstitutesTable() throws IOException
	{
		Map<String, Map<String, String>> values;
		try {
			values = filesIndex.readSheet("text", "key");
			values.forEach(ThrowingBiConsumer.sneaky((key, value) -> {
				options.substitutes.putIfAbsent(key, Optional.ofNullable(value.get("value"))
						.orElseThrow(() -> new IOException("Cannot find column: column=value"))
				);
			}));
		}
		catch (IOException ex) {
			throw new IOException("Cannot read substitutes table: " + ex, ex);
		}
	}

	private void readSubstitutesTableAndDate() throws IOException
	{
		if (mainOptions.getListFileKeys().size() != 1) {
			throw new IOException("index key must be specified exactly once (option -k)");
		}
		Matcher matcher = DATE_WITH_SUFFIX_PATTERN.matcher(mainOptions.getListFileKeys().get(0));
		if (!matcher.matches()) {
			throw new IOException("index key does not match date pattern yyyymmdd: " + mainOptions.getListFileKeys().get(0));
		}
		options.substitutes.computeIfAbsent("date", key ->
				LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)))
				.format(SWORN_DATE_FORMATTER)
		);
		readSubstitutesTable();
	}

	private File findPdfFile(String name) throws IOException
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

	private void renderExhibitId(PdfRenderer renderer, PDPageContentStream contentStream, PDPage page, InputEntry entry) throws IOException
	{
		entry.exhibitId = String.format("%c%c", exhibitCounter/26 + 'A', exhibitCounter%26 + 'A');
		String message = StringSubstitutor.replace(
				options.swornText,
				ImmutableMap.<String, String>builder()
						.putAll(options.substitutes)
						.put("exhibit", entry.exhibitId)
						.build(),
				"{",
				"}"
		);

		++exhibitCounter;

		boolean rotate = renderer.isRotated(page);
		float pageWidth = renderer.rotatedWidth(page);
		float pageHeight = renderer.rotatedHeight(page);

		PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
		float fontSize = 12.0f * (pageHeight/792.0f);

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
				options.firstExhibit + internalPageCounter - 1,
				stringWidth, stringHeight,
				pageWidth, pageHeight,
				xPosition, yPosition
		);

		contentStream.beginText();
		contentStream.setFont(font, fontSize);
		contentStream.setNonStrokingColor(0.1f, 0.1f, 0.5f);
		renderer.renderMultiLine(contentStream, page, xPosition, yPosition, stringHeight, message);
		contentStream.endText();
	}

	private static Integer parseExhibitId(String id)
	{
		int value = 0;
		if (id.isEmpty()) {
			throw new IllegalArgumentException("exhibitId must be non empty uppercase letter sequence");
		}
		for (int i = 0; i < id.length(); ++i) {
			char c = id.charAt(i);
			if (c < 'A' || c > 'Z') {
				throw new IllegalArgumentException("Invalid character in exhibitId, only uppercase letters allowed: " + c);
			}
			value = value * ('Z' - 'A' + 1) + (c - 'A');
		}
		return value;
	}

	private int executeCode() throws IOException
	{
		if (options.code.equals("")) {
			System.out.println("""
				 The following codes are available:
				 google-docs-exhibit-update.js - Google Docs exhibit update script
			""");
			return 0;
		}
		try (InputStream codeFile = JoinExhibitCommand.class.getResourceAsStream("JoinExhibitCommand-code/" + options.code)) {
			if (codeFile == null) {
				throw new IOException("Failed to open: " + "JoinExhibitCommand-code/" + options.code);
			}
			IOUtils.copy(codeFile, System.out);
		}
		return 0;
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.<String, String>builder()
				.put("--code id", "prints supporting code, omit the id for list")
				.put("--base base-document", "base document to start with")
				.put("-a page-number", "first page number (default 1)")
				.put("-s sworn-text", "sworn stamp text, can contain placeholders in {key} form")
				.put("--sa", "set sworn stamp text to affirmed, can contain placeholders in {key} form")
				.put("--ss", "set sworn stamp text to sworn, can contain placeholders in {key} form")
				.put("-t key=value", "substituted values for templates")
				.put("--tt", "read substituted values from 'text' sheet (key, value columns) from index file")
				.put("--ta", "read substituted values from 'text' sheet (key, value columns) from index file and date from first -k option (default)")
				.put("--tn", "do not read substituted values from Text sheet from index file")
				.put("--extract what (multi)", "extracts only subset of pages, possible values: first (first page) last (last page) exhibit-first (exhibit first pages) single (single page) pair-even (odd-even pair)")
				.put("-i", "ignore errors, such as file not found")
				.build();
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"inputs...", "files to copy"
		);
	}

	private TableUpdator filesIndex;

	private IndexReader indexReader;

	private int basePages = -1;

	private int internalPageCounter;

	private int exhibitCounter;

	public static class Options
	{
		private List<String> extract = new ArrayList<>();

		private String code;

		private String base;

		private List<String> inputs;

		private Integer firstPage = null;

		private Integer firstExhibit = null;

		private String swornText = null;

		private boolean ignoreMissing = false;

		private final Map<String, String> substitutes = new LinkedHashMap<>();

		private ThrowingRunnable<IOException> substituteSource;
	}

	@Builder
	static class InputEntry
	{
		public final String filename;
		float[] swornPosition;
		@Builder.Default
		int pageNumber = -1;
		@Builder.Default
		int internalPageNumber = -1;
		String exhibitId;
		String url;

		float width, height;

		Exception error;
	}
}
