package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.table.TableUpdator;
import com.github.kvr000.zbyneklegal.format.table.TableUpdatorFactory;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
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

	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
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
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		Map<String, InputEntry> files;

		filesIndex = tableUpdatorFactory.openTableUpdator(Paths.get(mainOptions.getListFile()), "Name");
		files = readListFile();

		List<File> toCompress = new ArrayList<>();

		for (Map.Entry<String, InputEntry> inputMapEntry: files.entrySet()) {
			InputEntry inputEntry = inputMapEntry.getValue();

			if (!Strings.isNullOrEmpty(inputEntry.filename)) {
				File file;
				try {
					file = findFile(inputEntry.filename);
					toCompress.add(file);
				} catch (FileNotFoundException ex) {
					log.error("Cannot find file: {}", inputEntry.filename);
				}
			}

			if (!Strings.isNullOrEmpty(inputEntry.media)) {
				File file;
				file = new File(inputEntry.media);
				if (file.exists()) {
					toCompress.add(file);
				}
				else {
					log.error("Cannot find file: {}", inputEntry.media);
				}
			}
		}

		int error = Runtime.getRuntime().exec(ImmutableList.<String>builder()
				.add("zip")
				.add("-s")
				.add("18m")
				.add("-9")
				.add(mainOptions.getOutput())
				.addAll(Lists.transform(toCompress, File::toString))
				.build().toArray(new String[0])
		).waitFor();

		if (error != 0) {
			throw new IOException("zip failed with code: " + error);
		}

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
		return filesIndex.listEntries().entrySet().stream()
				.filter(rec -> !rec.getKey().equals("BASE"))
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
