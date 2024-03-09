package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.github.kvr000.zbyneklegal.format.pdf.PdfFiles;
import com.google.common.base.Stopwatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Map;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PdfMetaCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

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
