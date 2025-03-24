package com.github.kvr000.zbyneklegal.format.command;

import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.dryuf.cmdline.command.AbstractCommand;
import net.dryuf.cmdline.command.CommandContext;
import org.apache.commons.io.IOUtils;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.ListIterator;
import java.util.Map;


@Log4j2
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class TabConfigToTextCommand extends AbstractCommand
{
	protected boolean parseOption(CommandContext context, String arg, ListIterator<String> args) throws Exception {
		return super.parseOption(context, arg, args);
	}

	@Override
	protected Map<String, String> configOptionsDescription(CommandContext context)
	{
		return ImmutableMap.<String, String>builder()
			.build();
	}

	@Override
	protected Map<String, String> configParametersDescription(CommandContext context)
	{
		return ImmutableMap.of(
		);
	}

	@Override
	public int execute() throws Exception
	{
		StringBuilder sb = new StringBuilder();
		for (String line: IOUtils.readLines(System.in, StandardCharsets.UTF_8)) {
			String fields[] = line.split("\t");
			if (fields.length < 2) {
				continue;
			}
			else if (fields[0].isBlank() && fields[1].isBlank()) {
				continue;
			}
			else if (fields[0].isBlank() || fields[1].isBlank()) {
				throw new IllegalArgumentException("Invalid input: " + line);
			}
			sb.append(fields[0]).append("=").append(fields[1]);
			if (fields.length > 2 && !fields[2].isBlank()) {
				sb.append("=").append(fields[2]);
			}
			sb.append(",");
		}

		System.out.println(sb);

		return EXIT_SUCCESS;
	}

	public static class Options
	{
	}
}
