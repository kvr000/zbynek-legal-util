package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.indexfile.IndexReader;
import com.github.kvr000.zbyneklegal.format.storage.StorageRepository;
import com.github.kvr000.zbyneklegal.format.table.TableUpdator;
import com.github.kvr000.zbyneklegal.format.table.TableUpdatorFactory;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.base.concurrent.executor.CloseableExecutor;
import net.dryuf.base.concurrent.executor.ClosingExecutor;
import net.dryuf.base.concurrent.executor.CommonPoolExecutor;
import net.dryuf.base.concurrent.future.FutureUtil;
import net.dryuf.base.function.ThrowingFunction;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

import javax.inject.Inject;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class SyncFilesCommand extends AbstractCommand
{
	public static final Map<String, ThrowingFunction<InputStream, InputStream, IOException>> COMPRESSIONS = ImmutableMap.of(
		"zst", ZstdCompressorInputStream::new,
		"gz", GzipCompressorInputStream::new,
		"xz", XZCompressorInputStream::new
	);

	private final TableUpdatorFactory tableUpdatorFactory;

	private final StorageRepository storageRepository;

	private final ZbynekLegalFormat.Options mainOptions;

	private Options options = new Options();

	private TableUpdator filesIndex;

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		}
		return super.parseOption(context, arg, args);
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getListFile() == null) {
			return usage(context, "-l index file is mandatory");
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

		AtomicInteger errors = new AtomicInteger();
		try (
				CloseableExecutor downloadExecutor = new ClosingExecutor(Executors.newSingleThreadExecutor());
				CloseableExecutor executor = CommonPoolExecutor.getInstance()
		) {
			for (Map.Entry<String, InputEntry> inputMapEntry: files.entrySet()) {
				InputEntry inputEntry = inputMapEntry.getValue();

				FutureUtil.submitAsync(() -> {
					if (inputEntry.filenameUrl == null) {
						return null;
					}
					return inputEntry;
				}, executor)
						.thenApplyAsync((InputEntry entry) -> {
							if (entry == null) {
								return null;
							}
							try {
								downloadPdf(inputEntry.filename + ".pdf", inputEntry.filenameUrl);
							}
							catch (Throwable ex) {
								log.error("Failed to download file: {}", inputEntry.filename, ex);
								errors.incrementAndGet();
							}
							return entry;
						}, downloadExecutor);

				FutureUtil.submitAsync(() -> {
							if (inputEntry.medianameUrl == null) {
								return null;
							}
							return inputEntry;
						}, executor)
						.thenApplyAsync((InputEntry entry) -> {
							if (entry == null) {
								return null;
							}
							try {
								downloadFile(inputEntry.medianame, inputEntry.medianameUrl);
							}
							catch (Throwable ex) {
								log.error("Failed to download file: {}", inputEntry.medianame, ex);
								errors.incrementAndGet();
							}
							return entry;
						}, downloadExecutor);
			}
		}

		log.info("Processed in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return errors.get() != 0 ? 1 : EXIT_SUCCESS;
	}

	private Map<String, InputEntry> readListFile() throws IOException
	{
			if (filesIndex.getHeaders().get("SHA256") == null) {
				throw new IllegalArgumentException("Key not found in index file: " + "SHA256");
			}
			return new IndexReader(filesIndex).readIndex(mainOptions.getListFileKeys().isEmpty() ? null : mainOptions.getListFileKeys()).entrySet().stream()
					.collect(ImmutableMap.toImmutableMap(
							Map.Entry::getKey,
							rec -> {
								try {
									return InputEntry.builder()
											.filename(rec.getKey())
											.filenameUrl(filesIndex.getUrl(rec.getKey(), "Name"))
											.medianame(rec.getValue().get("Media"))
											.medianameUrl(filesIndex.getUrl(rec.getKey(), "Media"))
											.build();
								}
								catch (Exception ex) {
									throw new IllegalArgumentException("Failed to process entry file=" + rec.getKey() + ": " + ex, ex);
								}
							}
					));
	}

	private void downloadFile(String localName, String url) throws IOException
	{
		log.info("Downloading any: local={} url={}", localName, url);
		try (InputStream stream = Files.newInputStream(Paths.get(localName))) {
			Map<String, String> metadata = storageRepository.metadata(url);
			if (compareChecksum(stream, metadata)) {
				return;
			}
		}
		catch (FileNotFoundException | NoSuchFileException ex) {
		}

		try (InputStream input = storageRepository.downloadFile(url)) {
			Files.copy(input, Paths.get(localName), StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private void downloadPdf(String localName, String url) throws IOException
	{
		String fullName;
		log.info("Downloading pdf: local={} url={}", localName, url);
		Map<String, String> metadata = storageRepository.metadata(url);
		ThrowingFunction<InputStream, InputStream, IOException> decompressor = null;
		if (metadata != null && (decompressor = COMPRESSIONS.get(metadata.get("fileExtension"))) != null) {
			fullName = localName + "." + metadata.get("fileExtension");
			try (InputStream stream = Files.newInputStream(Paths.get(fullName))) {
				if (compareChecksum(stream, metadata)) {
					return;
				}
			}
			catch (FileNotFoundException | NoSuchFileException ex) {
			}
		}
		else {
			fullName = localName;
			try (InputStream stream = Files.newInputStream(Paths.get(localName))) {
				if (compareChecksum(stream, metadata)) {
					return;
				}
			}
			catch (FileNotFoundException | NoSuchFileException ex) {
			}
		}

		try (InputStream input = storageRepository.downloadFile(url)) {
			Files.copy(input, Paths.get(fullName), StandardCopyOption.REPLACE_EXISTING);
		}

		if (decompressor != null) {
			try (
				InputStream compressed = Files.newInputStream(Paths.get(fullName));
				InputStream decompressed = decompressor.apply(compressed)
			) {
				Files.copy(decompressed, Paths.get(localName), StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	private boolean compareChecksum(InputStream stream, Map<String, String> metadata) throws IOException
	{
		final String localChecksum;
		String checksum;

		if (metadata == null) {
			return false;
		}

		if ((checksum = metadata.get("md5")) != null) {
			localChecksum = DigestUtils.md5Hex(stream);
		}
		else if ((checksum = metadata.get("sha256")) != null) {
			localChecksum = DigestUtils.sha256Hex(stream);
		}
		else {
			throw new IOException("Unsupported checksum algorithm: " + metadata);
		}

		return localChecksum.equals(checksum);
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
		);
	}

	public static class Options
	{
	}

	@Builder
	public static class InputEntry
	{
		String filename;

		String filenameUrl;

		String fileMd5;

		String medianame;

		String medianameUrl;

		String mediaChecksum;
	}
}
