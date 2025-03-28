package com.github.kvr000.zbyneklegal.format;

import com.github.kvr000.zbyneklegal.format.command.AddPageNumbersCommand;
import com.github.kvr000.zbyneklegal.format.command.DocIndexCommand;
import com.github.kvr000.zbyneklegal.format.command.JoinExhibitCommand;
import com.github.kvr000.zbyneklegal.format.command.MergeInkCommand;
import com.github.kvr000.zbyneklegal.format.command.PdfDecompressCommand;
import com.github.kvr000.zbyneklegal.format.command.PdfEmptyCommand;
import com.github.kvr000.zbyneklegal.format.command.PdfJoinCommand;
import com.github.kvr000.zbyneklegal.format.command.PdfMetaCommand;
import com.github.kvr000.zbyneklegal.format.command.PdfReplaceCommand;
import com.github.kvr000.zbyneklegal.format.command.PdfResizeCommand;
import com.github.kvr000.zbyneklegal.format.command.PdfSplitCommand;
import com.github.kvr000.zbyneklegal.format.command.SyncFilesCommand;
import com.github.kvr000.zbyneklegal.format.command.TabConfigToTextCommand;
import com.github.kvr000.zbyneklegal.format.command.UpdateChecksumCommand;
import com.github.kvr000.zbyneklegal.format.command.ZipCommand;
import com.github.kvr000.zbyneklegal.format.storage.DelegatingStorageRepository;
import com.github.kvr000.zbyneklegal.format.storage.googledrive.GoogleDriveStorageRepository;
import com.github.kvr000.zbyneklegal.format.storage.StorageRepository;
import com.github.kvr000.zbyneklegal.format.table.TableUpdatorFactory;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.app.AppContext;
import net.dryuf.cmdline.app.BeanFactory;
import net.dryuf.cmdline.app.CommonAppContext;
import net.dryuf.cmdline.app.guice.GuiceBeanFactory;
import net.dryuf.cmdline.command.AbstractParentCommand;
import net.dryuf.cmdline.command.Command;
import net.dryuf.cmdline.command.CommandContext;
import net.dryuf.cmdline.command.HelpOfHelpCommand;
import net.dryuf.cmdline.command.RootCommandContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;


/**
 * ZbynekLegalFormat entry point.  This class only executes subcommands.
 */
@RequiredArgsConstructor(onConstructor = @__(@Inject))
@Log4j2
public class ZbynekLegalFormat extends AbstractParentCommand
{
	private Options options;

	public static void main(String[] args)
	{
		runMain(args, (args0) -> {
			AppContext appContext = new CommonAppContext(Guice.createInjector(new GuiceModule()).getInstance(BeanFactory.class));
			return appContext.getBeanFactory().getBean(ZbynekLegalFormat.class).run(
				new RootCommandContext(appContext).createChild(null, "zbynek-legal-format", null),
				Arrays.asList(args0)
			);
		});
	}

	protected CommandContext createChildContext(CommandContext commandContext, String name, boolean isHelp)
	{
		return commandContext.createChild(this, name, Map.of(Options.class, options));
	}

	@Override
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception
	{
		switch (arg) {
		case "-o":
			options.output = needArgsParam(options.output, args);
			return true;

		case "-l":
			options.listFile = needArgsParam(options.listFile, args);
			return true;

		case "-s":
			options.listSheet = needArgsParam(options.listSheet, args);
			return true;

		case "-k":
			options.listFileKeys.add(needArgsParam(null, args));
			return true;

		default:
			return super.parseOption(context, arg, args);
		}
	}

	@Override
	public void createOptions(CommandContext context)
	{
		this.options = new Options();
	}

	@Override
	protected String configHelpTitle(CommandContext context)
	{
		return "zbynek-legal-format - various legal formatting tools";
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"-o output", "output filename",
			"-l list-file", "index file",
			"-s list-sheet", "index sheet",
			"-k column-name", "multiple, key column in index file for the specific operation, suffixed with Exh and Pg"
		);
	}

	@Override
	protected Map<String, Class<? extends Command>> configSubCommands(CommandContext context)
	{
		return ImmutableMap.<String, Class<? extends Command>>builder()
			.put("join-exhibit", JoinExhibitCommand.class)
			.put("doc-index", DocIndexCommand.class)
			.put("update-checksum", UpdateChecksumCommand.class)
			.put("sync-files", SyncFilesCommand.class)
			.put("tab-config-to-text", TabConfigToTextCommand.class)
			.put("zip", ZipCommand.class)
			.put("add-page-numbers", AddPageNumbersCommand.class)
			.put("pdf-join", PdfJoinCommand.class)
			.put("pdf-split", PdfSplitCommand.class)
			.put("pdf-replace", PdfReplaceCommand.class)
			.put("pdf-resize", PdfResizeCommand.class)
			.put("pdf-meta", PdfMetaCommand.class)
			.put("pdf-decompress", PdfDecompressCommand.class)
			.put("pdf-empty", PdfEmptyCommand.class)
			.put("merge-ink", MergeInkCommand.class)
			.put("help", HelpOfHelpCommand.class)
			.build();
	}

	protected Map<String, String> configCommandsDescription(CommandContext context)
	{
		return ImmutableMap.<String, String>builder()
			.put("join-exhibit", "Concatenates exhibit files into single document, adding page numbers and updates index")
			.put("doc-index", "Index documents per category")
			.put("update-checksum", "Calculates files checksum and updates index")
			.put("sync-files", "Synchronize files from remote storage")
			.put("tab-config-to-text", "Converts TSV tab config into tab=code,... string")
			.put("zip", "Zips files into multiarchive")
			.put("add-page-numbers", "Add page numbers and merge the files")
			.put("pdf-join", "Joins pdf files")
			.put("pdf-split", "Splits pdf by size or number of pages")
			.put("pdf-replace", "Replaces pages in pdf")
			.put("pdf-resize", "Resizes image based pdf")
			.put("pdf-meta", "Shows meta fields from pdf")
			.put("pdf-decompress", "Internally decompresses the pdf")
			.put("pdf-empty", "Creates empty (zero pages) pdf")
			.put("merge-ink", "Merges ink from printed pages into original document")
			.put("help [command]", "Prints help")
			.build();
	}

	@Data
	public static class Options
	{
		String output;

		String listFile;

		String listSheet;

		List<String> listFileKeys = new ArrayList<>();
	}

	public static class GuiceModule extends AbstractModule
	{
		@Override
		@SneakyThrows
		protected void configure()
		{
			bind(TableUpdatorFactory.class);
		}

		@Provides
		@Singleton
		public StorageRepository storageRepository() throws IOException {
			return new DelegatingStorageRepository(ImmutableMap.of(
					"https://drive.google.com/", Suppliers.memoize(GoogleDriveStorageRepository::new)
			));
		}

		@Provides
		public BeanFactory beanFactory(Injector injector)
		{
			return new GuiceBeanFactory(injector);
		}
	}
}
