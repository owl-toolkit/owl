/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.run;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.StringJoiner;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import owl.run.modules.OwlModuleRegistry;
import owl.run.parser.OwlParser;
import owl.util.GuardedStream;

public final class DefaultCli {
  private DefaultCli() {}

  public static void main(String... args) throws IOException {
    OwlParser parseResult = OwlParser.parse(args, new DefaultParser(), getOptions(),
      OwlModuleRegistry.DEFAULT_REGISTRY);
    if (parseResult == null) {
      System.exit(1);
      return;
    }

    run(parseResult.globalSettings, parseResult.pipeline);
  }

  public static Options getOptions() {
    Option fileInput = new Option("I", "filein", true,
      "Read input from the specified file (- for stdin)");
    Option fixedInput = new Option("i", "input", true, "Use given strings as input");
    OptionGroup inputGroup = new OptionGroup().addOption(fileInput).addOption(fixedInput);
    Option fileOutput = new Option("O", "fileout", true,
      "Write output to the specified file (- for stdout)");

    return new Options()
      .addOptionGroup(inputGroup)
      .addOption(fileOutput)
      .addOption(RunUtil.getDefaultAnnotationOption());
  }

  public static void run(CommandLine settings, Pipeline pipeline) throws IOException {
    ReadableByteChannel reader;

    if (settings.hasOption("filein")) {
      String[] sources = settings.getOptionValues("filein");

      if (sources.length != 1) {
        throw RunUtil.failWithMessage("Multiple sources specified");
      }

      if (sources[0].trim().equals("-")) {
        reader = Channels.newChannel(System.in);
      } else {
        reader = Files.newByteChannel(Paths.get(sources[0]), StandardOpenOption.READ);
      }
    } else if (settings.hasOption("input")) {
      reader = createReader(List.of(settings.getOptionValues("input")));
    } else if (!settings.getArgList().isEmpty()) {
      reader = createReader(settings.getArgList());
      settings.getArgList().clear();
    } else {
      reader = Channels.newChannel(System.in);
    }

    String destination = settings.getOptionValue("fileout");
    @SuppressWarnings("resource")
    WritableByteChannel writer = destination == null || "-".equals(destination)
      ? Channels.newChannel(GuardedStream.sysout)
      : Files.newByteChannel(Paths.get(destination),
        StandardOpenOption.APPEND, StandardOpenOption.CREATE);

    try (reader; writer) {
      pipeline.run(reader, writer);
    }
  }

  @SuppressWarnings("resource")
  private static ReadableByteChannel createReader(List<String> inputs) {
    // Build the byte array now, since the inputs list may get cleared after this call.
    StringJoiner joiner = new StringJoiner(System.lineSeparator());
    inputs.forEach(joiner::add);
    byte[] bytes = joiner.toString().getBytes(StandardCharsets.UTF_8);
    return Channels.newChannel(new ByteArrayInputStream(bytes));
  }
}
