package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.format.SizeFormat;
import com.github.kvr000.zbyneklegal.format.indexfile.IndexReader;
import com.github.kvr000.zbyneklegal.format.table.TableUpdator;
import com.github.kvr000.zbyneklegal.format.table.TableUpdatorFactory;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.compress.archivers.zip.StreamCompressor;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.parallel.ScatterGatherBackingStore;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.Pair;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class ZipCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

	private final TableUpdatorFactory tableUpdatorFactory;

	private Options options = new Options();

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		case "-s":
			options.maxPart = SizeFormat.parseSize(needArgsParam(options.maxPart, args));
			return true;

		case "-a":
			options.maxArchive = SizeFormat.parseSize(needArgsParam(options.maxPart, args));
			return true;
		}
		return super.parseOption(context, arg, args);
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getListFile() == null) {
			return usage(context, "-l index file is mandatory");
		}
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output file is mandatory");
		}
		if (options.maxPart != null && options.maxArchive != null) {
			return usage(context, "-s and -a cannot be specified both");
		}
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		Map<String, InputEntry> files;

		filesIndex = tableUpdatorFactory.openTableUpdator(Paths.get(mainOptions.getListFile()), mainOptions.getListSheet(), "Name");
		files = readListFile();

		List<Path> toCompress = new ArrayList<>();
		for (Map.Entry<String, InputEntry> inputMapEntry : files.entrySet()) {
			InputEntry inputEntry = inputMapEntry.getValue();

			if (!Strings.isNullOrEmpty(inputEntry.filename)) {
				File file = null;
				try {
					file = findFile(inputEntry.filename);
				} catch (FileNotFoundException ex) {
					log.error("Cannot find file: {}", inputEntry.filename);
				}
				if (file != null) {
					toCompress.add(file.toPath());
				}
			}

			if (!Strings.isNullOrEmpty(inputEntry.media)) {
				Path file = Paths.get(inputEntry.media);
				if (Files.exists(file)) {
					toCompress.add(file);
				} else {
					log.error("Cannot find file: {}", inputEntry.media);
				}
			}
		}

		if (options.maxPart != null) {
			try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(Paths.get(mainOptions.getOutput()), options.maxPart)) {
				archive.setEncoding("UTF-8");
				for (Path file: toCompress) {
					addArchiveFile(archive, file);
				}
			}
		}
		else if (options.maxArchive != null) {
			for (int archiveCounter = 0; !toCompress.isEmpty(); ++archiveCounter) {
				try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(Paths.get(FilenameUtils.removeExtension(mainOptions.getOutput()) + String.format("-%04d.", archiveCounter) + FilenameUtils.getExtension(mainOptions.getOutput())))) {
					archive.setEncoding("UTF-8");

					for (int entriesCounter = 0; !toCompress.isEmpty(); ++entriesCounter) {
						Pair<ZipArchiveEntry, ByteArrayOutputStream> entry = createRawEntry(archive, toCompress.get(0));

						if (archive.getBytesWritten() + entry.getValue().size() + 512L*(entriesCounter+1) > options.maxArchive) {
							break;
						}
						archive.addRawArchiveEntry(entry.getKey(), entry.getValue().toInputStream());
						toCompress.remove(0);
					}
				}
			}
		}
		else {
			try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(Paths.get(mainOptions.getOutput()))) {
				archive.setEncoding("UTF-8");

				while (!toCompress.isEmpty()) {
					Pair<ZipArchiveEntry, ByteArrayOutputStream> entry = createRawEntry(archive, toCompress.get(0));

					archive.addRawArchiveEntry(entry.getKey(), entry.getValue().toInputStream());
					toCompress.remove(0);
				}
			}
		}

		log.info("Processed in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	private Map<String, InputEntry> readListFile() throws IOException
	{
		if (filesIndex.getHeaders().get("Media") == null) {
			throw new IllegalArgumentException("Key not found in index file: " + "Media");
		}
		return new IndexReader(filesIndex).readIndex(mainOptions.getListFileKeys().isEmpty() ? null : mainOptions.getListFileKeys()).entrySet().stream()
				.collect(ImmutableMap.toImmutableMap(
						Map.Entry::getKey,
						rec -> {
							try {
								return InputEntry.builder()
										.filename(rec.getKey())
										.media(rec.getValue().get("Media"))
										.build();
							} catch (Exception ex) {
								throw new IllegalArgumentException("Failed to process entry file=" + rec.getKey() + ": " + ex, ex);
							}
						}
				));
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

	private void addArchiveFile(ZipArchiveOutputStream archive, Path file) throws IOException
	{
		Pair<ZipArchiveEntry, ByteArrayOutputStream> entry = createRawEntry(archive, file);
		archive.addRawArchiveEntry(entry.getKey(), entry.getValue().toInputStream());
	}

	private Pair<ZipArchiveEntry, ByteArrayOutputStream> createRawEntry(ZipArchiveOutputStream archive, Path file) throws IOException
	{
		ZipArchiveEntry entry = archive.createArchiveEntry(file, file.getFileName().toString());
		ByteArrayBackingStore deflated = new ByteArrayBackingStore();
		StreamCompressor compressor = StreamCompressor.create(9, deflated);
		try (InputStream input = Files.newInputStream(file)) {
			compressor.deflate(input, ZipArchiveEntry.DEFLATED);
			compressor.close();
		}
		entry.setMethod(ZipArchiveEntry.DEFLATED);
		entry.setCrc(compressor.getCrc32());
		entry.setCompressedSize(deflated.output.size());

		return Pair.of(entry, deflated.output);
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
		);
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context) {
		return ImmutableMap.of(
				"-s max-part-size[BKMGT]", "max size of archive part, default is bytes",
				"-a max-archive-size[BKMGT]", "max size of one archive, default is bytes"
		);
	}

	private TableUpdator filesIndex;

	public static class Options
	{
		Long maxPart;

		Long maxArchive;
	}

	@Builder
	static class InputEntry
	{
		public final String filename;

		String checksum;

		public final String media;

		String mediaChecksum;
	}

	static class ByteArrayBackingStore implements ScatterGatherBackingStore
	{
		ByteArrayOutputStream output = new ByteArrayOutputStream();

		@Override
		public void closeForWriting() throws IOException {
			output.close();
		}

		@Override
		public InputStream getInputStream() throws IOException {
			return output.toInputStream();
		}

		@Override
		public void writeOut(byte[] data, int offset, int length) throws IOException {
			output.write(data, offset, length);
		}

		@Override
		public void close() throws IOException {
			output.close();
		}
	}
}
