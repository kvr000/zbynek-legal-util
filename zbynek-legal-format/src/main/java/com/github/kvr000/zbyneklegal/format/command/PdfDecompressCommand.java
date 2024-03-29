package com.github.kvr000.zbyneklegal.format.command;

import com.github.kvr000.zbyneklegal.format.ZbynekLegalFormat;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDStream;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class PdfDecompressCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

	private final Options options = new Options();

	@Override
	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		options.inputs = ImmutableList.copyOf(args);
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}

		if (options.inputs.size() != 1) {
			return usage(context, "input file is mandatory");
		}

		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		try (PDDocument doc = Loader.loadPDF(new File(options.inputs.get(0)))) {
			doc.setAllSecurityToBeRemoved(true);
			COSDocument cosDocument = doc.getDocument();
			cosDocument.getXrefTable().keySet()
				.forEach(o -> processObject(cosDocument.getObjectFromPool(o)));
			doc.getDocumentCatalog();
			doc.getDocument().setIsXRefStream(false);
			doc.save(mainOptions.getOutput());
		}

		log.info("Processed in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	private void processObject(COSObject cosObject)
	{
		COSBase base = cosObject.getObject();
		if (base instanceof COSStream stream) {
			if (COSName.XOBJECT.equals(stream.getItem(COSName.TYPE))
				&& COSName.IMAGE.equals(stream.getItem(COSName.SUBTYPE)))
			{
				return;
			}
			try {
				byte[] bytes = new PDStream(stream).toByteArray();
				stream.removeItem(COSName.FILTER);
				try (OutputStream streamOut = stream.createOutputStream()) {
					streamOut.write(bytes);
				}
			}
			catch (IOException ex) {
				log.warn("skip object={} generation={}", cosObject.getObjectNumber(), cosObject.getGenerationNumber(), ex);
			}
		}
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.of(
		);
	}

	public static class Options
	{
		List<String> inputs;

		Integer firstPage;

		float[] pagePosition;

		String pagePattern;
	}
}
