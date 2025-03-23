package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.file.DirTreeFileDb;
import com.github.kvr000.zbyneklegal.format.file.FileDb;
import com.github.kvr000.zbyneklegal.format.format.StringFormat;
import com.github.kvr000.zbyneklegal.format.indexfile.IndexReader;
import com.github.kvr000.zbyneklegal.format.pdf.PdfFiles;
import com.github.kvr000.zbyneklegal.format.pdf.PdfRenderer;
import com.github.kvr000.zbyneklegal.format.table.TableUpdator;
import com.github.kvr000.zbyneklegal.format.table.TableUpdatorFactory;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.text.StringSubstitutor;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class DocIndexCommand extends AbstractCommand
{
	public static final String EXHIBIT_TEXT =
	"""
			This is Exhibit "{category}.{exhibit}"
			""";

	private final PdfFiles pdfFiles;

	private final TableUpdatorFactory tableUpdatorFactory;

	private final ZbynekLegalFormat.Options mainOptions;

	private Options options = new Options();

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
		return super.parseOption(context, arg, args);
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.<String, String>builder()
			.build();
	}

	@Override
	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
		);
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getListFile() == null) {
			return usage(context, "list file must be provided");
		}
		if (mainOptions.getListFileKeys().size() != 1) {
			return usage(context, "one list file key must be provided");
		}
		return EXIT_CONTINUE;
	}

	protected void revalidateOptions() throws Exception
	{
		if (mainOptions.getOutput() == null) {
			throw new IllegalArgumentException("output must be provided, either via option or Pg/FILES");
		}
	}
	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		filesIndex = tableUpdatorFactory.openTableUpdator(Paths.get(mainOptions.getListFile()), mainOptions.getListSheet(), "Name");
		indexReader = new IndexReader(filesIndex);
		final Map<String, InputEntry> files = readListFile();
		readIndexConfig();
		readIndexIo();
		readTabMap();

		revalidateOptions();

		LinkedHashMap<String, String> errors = new LinkedHashMap<>();

		Files.createDirectories(Paths.get(mainOptions.getOutput()));

		for (InputEntry file: files.values()) {
			final Category category = categoryMap.get(file.category);
			if (category == null) {
				if (true) {
					errors.put(file.filename, "Unknown category for file: file=" + file.filename + " category=" + file.category);
					continue;
				}
				throw new IllegalArgumentException("Unknown category for file: file=" + file.filename + " category=" + file.category);
			}
			if (filesIndex.getOptionalConfig("NEEDURL", "Name").map(Integer::parseInt).filter(v -> v == 0).isEmpty() && file.url == null) {
				continue;
			}
			try (PDDocument input = pdfFiles.load(findPdfFile(file.filename))) {
				if (category.output == null) {
					category.output = new PDDocument();
					category.merger = new PDFMergerUtility();
					category.renderer = new PdfRenderer(category.output);
				}
				file.categoryPage0 = category.categoryPages;
				category.merger.appendDocument(category.output, input);
				if (input.getNumberOfPages() % 2 == 1) {
					category.output.addPage(new PDPage(input.getPage(0).getMediaBox()));
				}
				file.exhibitId = generateExhibitId(category);
				category.renderer.rotatePagesPortrait(category.categoryPages, category.output.getNumberOfPages());
				for (int i = category.categoryPages; i < category.output.getNumberOfPages(); ++i) {
					PDPage page = category.output.getPage(i);
					try (PDPageContentStream contentStream = new PDPageContentStream(category.output, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
						if (i == category.categoryPages) {
							renderExhibitId(category.renderer, contentStream, page, file);
						}
						category.renderer.renderPageNumber(contentStream, page, StringFormat.escapePercent(category.code) + " %03d", i + 1);
					}
				}
				category.categoryPages = category.output.getNumberOfPages();
				file.categoryId = category.tab + String.format("-%03d", ++category.categoryCount);
			}
		}

		for (Category category: categoryMap.values()) {
			if (category.output != null) {
				category.output.save(Paths.get(mainOptions.getOutput()).resolve(category.code+".pdf").toFile());
			}
		}
		errors.forEach((file, message) -> log.error(message));
		for (Category category: categoryMap.values()) {
			System.out.println(category.code + "\t" + category.categoryCount + "\t" + category.categoryPages);
		}
		updateListFile(files);

		log.info("Completed in: {} ms", watch.elapsed(TimeUnit.MILLISECONDS));

		return EXIT_SUCCESS;
	}

	private String generateExhibitId(Category category)
	{
		int counter = category.exhibitCount++;
		String exhibitId = String.format("%c%c", counter/26 + 'A', counter%26 + 'A');
		return exhibitId;
	}

	private void renderExhibitId(PdfRenderer renderer, PDPageContentStream contentStream, PDPage page, InputEntry entry) throws IOException
	{
		String message = StringSubstitutor.replace(
			EXHIBIT_TEXT,
			ImmutableMap.<String, String>builder()
				.put("category", entry.category)
				.put("exhibit", entry.exhibitId)
				.build(),
			"{",
			"}"
		);

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

		contentStream.beginText();
		contentStream.setFont(font, fontSize);
		contentStream.setNonStrokingColor(0.1f, 0.1f, 0.5f);
		renderer.renderMultiLine(contentStream, page, xPosition, yPosition, stringHeight, message);
		contentStream.endText();
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
							.category(Optional.ofNullable(rec.getValue().get("Category")).orElseThrow(() -> new IllegalArgumentException("Category not specified for: " + rec.getKey())))
							.swornPosition(Optional.ofNullable(rec.getValue().get("Sworn Pos"))
								.map(Strings::emptyToNull)
								.map(pos -> {
									if (pos.equals("default")) {
										return null;
									}
									String[] split = pos.split(";", 2);
									if (split.length != 2) {
										throw new IllegalArgumentException("Sworn Pos must contain two semicolon separated numbers or be 'default', got: "+pos);
									}
									return new float[]{Float.parseFloat(split[0]), Float.parseFloat(split[1])};
								})
								.orElse(null)
							)
							.build();
					}
					catch (Exception ex) {
						throw new IllegalArgumentException("Failed to process entry file="+rec.getKey()+": "+ex, ex);
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
							filesIndex.setValue(entry.filename, exhibitKey + " Pg", Integer.toString(entry.categoryPage0));
							filesIndex.setValue(entry.filename, exhibitKey + " Exh", Optional.ofNullable(entry.categoryId).orElse("-"));
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
	}

	private void readIndexIo() throws IOException
	{
		if (mainOptions.getOutput() == null) {
			if (mainOptions.getListFileKeys().isEmpty()) {
				throw new IOException("No output file provided, nor index key");
			}
			mainOptions.setOutput(
				filesIndex.getOptionalConfig("FILES", mainOptions.getListFileKeys().get(0) + " Pg")
					.filter(Predicate.not(Strings::isNullOrEmpty))
					.orElse(null)
			);
		}
	}

	private void readTabMap() throws IOException
	{
		String tabMapStr = filesIndex.getConfig("TABMAP", mainOptions.getListFileKeys().get(0) + " Exh");
		if (tabMapStr == null) {
			throw new IOException("TABMAP not found for key: " + mainOptions.getListFileKeys().get(0) +" Exh");
		}
		categoryMap =  Arrays.stream(tabMapStr.split(","))
			.filter(s -> !s.isBlank())
			.map(s -> {
				String[] p = s.split("=", 2);
				return Category.builder()
					.code(p[0])
					.tab(p[1])
					.build();
			})
			.collect(ImmutableMap.toImmutableMap(Category::getCode, Function.identity()));
	}

	private Path findPdfFile(String name) throws IOException
	{
		return pdfFileDb.findFile(name);
	}

	private TableUpdator filesIndex;

	private IndexReader indexReader;

	private FileDb pdfFileDb = new DirTreeFileDb(Paths.get("."), ".pdf");

	private Map<String, Category> categoryMap;

	public static class Options
	{
		int align = 0;
	}

	@Builder
	@ToString
	static class InputEntry
	{
		public final String filename;
		public final String url;
		public final String category;
		float[] swornPosition;
		String categoryId;
		int categoryPage0;
		String exhibitId;
	}

	@Builder
	@Data
	static class Category
	{
		String tab;
		String code;
		String name;
		int categoryCount;
		int categoryPages;
		int exhibitCount;

		PDFMergerUtility merger;
		PDDocument output;
		PdfRenderer renderer;
	}
}
