package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.google.common.base.Stopwatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import javax.inject.Inject;
import java.io.File;
import java.util.ListIterator;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PdfMetaCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}

		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		try (PDDocument doc = Loader.loadPDF(new File(mainOptions.getOutput()))) {
			for (String key: doc.getDocumentInformation().getMetadataKeys()) {
				System.out.println(key + ": " + doc.getDocumentInformation().getCustomMetadataValue(key));
			}
		}

		return EXIT_SUCCESS;
	}
}
