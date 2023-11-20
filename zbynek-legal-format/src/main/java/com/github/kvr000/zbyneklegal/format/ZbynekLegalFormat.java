package com.github.kvr000.zbyneklegal.format;

import com.github.kvr000.zbyneklegal.format.command.*;
import com.github.kvr000.zbyneklegal.format.storage.googledrive.DelegatingStorageRepository;
import com.github.kvr000.zbyneklegal.format.storage.googledrive.GoogleDriveStorageRepository;
import com.github.kvr000.zbyneklegal.format.storage.googledrive.StorageRepository;
import com.github.kvr000.zbyneklegal.format.table.TableUpdatorFactory;
import com.google.common.collect.ImmutableMap;
import com.google.inject.*;
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
import java.util.Arrays;
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

		case "-k":
			options.listFileKey = needArgsParam(options.listFileKey, args);
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
			"-l list file", "index with files",
			"-k column-name", "key column in index file for the specific operation, suffixed with Exh and Pg"
		);
	}

	@Override
	protected Map<String, Class<? extends Command>> configSubCommands(CommandContext context)
	{
		return ImmutableMap.of(
			"join-exhibit", JoinExhibitCommand.class,
			"update-checksum", UpdateChecksumCommand.class,
			"add-page-numbers", AddPageNumbersCommand.class,
			"sync-files", SyncFilesCommand.class,
			"zip", ZipCommand.class,
			"help", HelpOfHelpCommand.class
		);
	}

	protected Map<String, String> configCommandsDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"join-exhibit", "Concatenates exhibit files into single document, adding page numbers and updates index",
			"update-checksum", "Calculates files checksum and updates index",
			"add-page-numbers", "Add page numbers and merge the files",
			"sync-files", "Synchronize files from remote storage",
			"zip", "Zips files into multiarchive",
			"help [command]", "Prints help"
		);
	}

	@Data
	public static class Options
	{
		String output;

		String listFile;

		String listFileKey;
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
					"https://drive.google.com/", new GoogleDriveStorageRepository()
			));
		}

		@Provides
		public BeanFactory beanFactory(Injector injector)
		{
			return new GuiceBeanFactory(injector);
		}
	}
}
