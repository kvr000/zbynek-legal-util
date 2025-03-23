package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.indexfile.IndexReader;
import com.github.kvr000.zbyneklegal.format.table.TableUpdator;
import com.github.kvr000.zbyneklegal.format.table.TableUpdatorFactory;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.base.concurrent.executor.CloseableExecutor;
import net.dryuf.base.concurrent.executor.CommonPoolExecutor;
import net.dryuf.base.concurrent.future.FutureUtil;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.codec.digest.DigestUtils;

import javax.inject.Inject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class UpdateChecksumCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

	private final TableUpdatorFactory tableUpdatorFactory;

	private Options options = new Options();

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
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

		try (CloseableExecutor executor = CommonPoolExecutor.getInstance()) {
			for (Map.Entry<String, InputEntry> inputMapEntry: files.entrySet()) {
				InputEntry inputEntry = inputMapEntry.getValue();
				FutureUtil.submitAsync(() -> {
							if (!Strings.isNullOrEmpty(inputEntry.filename)) {
						File file;
						try {
							file = findFile(inputEntry.filename);
						} catch (FileNotFoundException ex) {
							log.error("Cannot find file: {}", inputEntry.filename);
							return null;
						}
						try (InputStream stream = Files.newInputStream(file.toPath())) {
							inputEntry.checksum = DigestUtils.sha256Hex(stream);
						}
					}
					return null;
				}, executor)
						.exceptionally((ex) -> {
							log.error("Failed to process file: {}", inputEntry.filename, ex);
							return null;
						});
				FutureUtil.submitAsync(() -> {
					if (!Strings.isNullOrEmpty(inputEntry.media)) {
						File file;
						file = new File(inputEntry.media);
						if (!file.exists()) {
							log.error("Cannot find file: {}", inputEntry.media);
							return null;
						}
						try (InputStream stream = Files.newInputStream(file.toPath())) {
							inputEntry.mediaChecksum = DigestUtils.sha256Hex(stream);
						}
					}
					return null;
				}, executor)
						.exceptionally((ex) -> {
							log.error("Failed to process file: {}", inputEntry.media, ex);
							return null;
						});
				;
			}
		}

		updateListFile(files);

		log.info("Processed in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	private Map<String, InputEntry> readListFile() throws IOException
	{
		if (filesIndex.getHeaders().get("SHA256") == null) {
			throw new IllegalArgumentException("Key not found in index file: " + "SHA256");
		}
		if (filesIndex.getHeaders().get("Media") == null) {
			throw new IllegalArgumentException("Key not found in index file: " + "Media");
		}
		if (filesIndex.getHeaders().get("Media SHA256") == null) {
			throw new IllegalArgumentException("Key not found in index file: " + "Media SHA256");
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

	private void updateListFile(Map<String, InputEntry> files) throws IOException
	{
		files.values().forEach(entry -> {
			if (entry.checksum != null) {
				filesIndex.setValue(entry.filename, "SHA256", entry.checksum);
			}
			if (entry.mediaChecksum != null) {
				filesIndex.setValue(entry.filename, "Media SHA256", entry.mediaChecksum);
			}
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

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
		);
	}

	private TableUpdator filesIndex;

	public static class Options
	{
	}

	@Builder
	static class InputEntry
	{
		public final String filename;

		String checksum;

		public final String media;

		String mediaChecksum;
	}
}
