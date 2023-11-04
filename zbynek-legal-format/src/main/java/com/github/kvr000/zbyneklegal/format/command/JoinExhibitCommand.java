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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class JoinExhibitCommand extends AbstractCommand
{
	private final ZbynekLegalFormat.Options mainOptions;

	private Options options = new Options();

	@Override
	protected int parseNonOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		ImmutableList<String> remaining = ImmutableList.copyOf(args);
		if (remaining.size() < 1) {
			return usage(context, "Need one or more parameters as source files");
		}
		options.inputs = remaining;
		return EXIT_CONTINUE;
	}

	@Override
	protected int validateOptions(CommandContext context, ListIterator<String> args) throws Exception
	{
		if (mainOptions.getOutput() == null) {
			return usage(context, "-o output option is mandatory");
		}
		if (options.inputs == null) {
			return usage(context, "input files required");
		}
		return EXIT_CONTINUE;
	}

	@Override
	public int execute() throws Exception
	{
		Stopwatch watch = Stopwatch.createStarted();

		try (PDDocument doc = Loader.loadPDF(new File(options.inputs.get(0)))) {
			PDPageTree allPages = doc.getDocumentCatalog().getPages();
			PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
			float fontSize = 20.0f;

			for (int i = 0; i < allPages.getCount(); i++) {
				String message = String.format("Pg %03d", options.firstPage+i);
				PDPage page = allPages.get(i);
				PDRectangle pageSize = page.getMediaBox();
				float stringWidth = font.getStringWidth(message)*fontSize/1000f;
				float stringHeight = font.getHeight('A')*fontSize/1000f;
				// calculate to center of the page
				int rotation = page.getRotation();
				boolean rotate = rotation == 90 || rotation == 270;
				float pageWidth = rotate ? pageSize.getHeight() : pageSize.getWidth();
				float pageHeight = rotate ? pageSize.getWidth() : pageSize.getHeight();
				float xPosition = rotate ? (stringHeight + 10) : (pageWidth - stringWidth - 10);
				float yPosition = rotate ? (pageWidth - stringWidth - 10) : (pageHeight - stringHeight - 10);
				System.out.printf("sw=%g sh=%g width=%g height=%g x=%g y=%g\n",
						stringWidth, stringHeight,
						pageWidth, pageHeight,
						xPosition, yPosition
				);
				// append the content to the existing stream
				PDPageContentStream contentStream = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true,true);
				contentStream.beginText();
				// set font and font size
				contentStream.setFont( font, fontSize );
				// set text color to red
				contentStream.setNonStrokingColor(0.5f, 0.5f, 1);
				if (rotate) {
					// rotate the text according to the page rotation
					contentStream.setTextMatrix(Matrix.getRotateInstance(Math.PI/2, xPosition, yPosition));
				}
				else {
					contentStream.setTextMatrix(Matrix.getTranslateInstance(xPosition, yPosition));
				}
				contentStream.showText(message);
				contentStream.endText();
				contentStream.close();
			}

			doc.save(mainOptions.getOutput());
		}

		log.info("Written output in {} ms", watch.elapsed(TimeUnit.MILLISECONDS));
		return EXIT_SUCCESS;
	}

	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
			"inputs...", "files to copy"
		);
	}

	public static class Options
	{
		private List<String> inputs;

		private int firstPage = 1;
	}
}
